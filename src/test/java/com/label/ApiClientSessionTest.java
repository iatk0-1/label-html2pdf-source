package com.label;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiClientSessionTest {
    HttpServer server;
    ApiClient client;
    List<String> authorizations;
    AtomicBoolean revoked;

    @BeforeEach void initialize() throws IOException {
        authorizations = java.util.Collections.synchronizedList(new ArrayList<>());
        revoked = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/printer-accounts/login", exchange -> respond(exchange, 200,
                "{\"userId\":\"9007199254741999\",\"accessToken\":\"fixture-printer-token\",\"tokenType\":\"Bearer\"}"));
        server.createContext("/api/v1/printer-accounts/qrcode/check/ticket", exchange -> respond(exchange, 200,
                "{\"status\":\"authorized\",\"userId\":\"9007199254741999\",\"accessToken\":\"fixture-qr-token\"}"));
        server.createContext("/api/v1/waybills", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (revoked.get()) respond(exchange, 403, "{\"message\":\"当前打印岗位权限已被收回\"}");
            else if ("POST".equals(exchange.getRequestMethod())) respond(exchange, 204, "");
            else respond(exchange, 200, "{\"items\":[],\"total\":0,\"page\":0,\"size\":20}");
        });
        server.start();
        client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ApiClient.WaybillQuery query() {
        return new ApiClient.WaybillQuery(0, 20, null, "ALL", null, null, null, null, null, null);
    }

    @Test void passwordLoginCarriesCredentialForQueriesAndEveryPrintStatus() throws Exception {
        assertTrue(client.login("fixture", "fixture-password"));
        assertEquals(9007199254741999L, client.getUserId());
        assertEquals(0, client.fetchWaybillPage(query()).total());
        client.markGenerated(42L);
        client.markPrinted(42L);
        client.markPrintFailed(42L);
        assertEquals(List.of("Bearer fixture-printer-token", "Bearer fixture-printer-token",
                "Bearer fixture-printer-token", "Bearer fixture-printer-token"), authorizations);
    }

    @Test void qrResponseCreatesTheSameAuthenticatedClientSession() throws Exception {
        client.acceptSession(JsonParser.parseString(client.get("/api/v1/printer-accounts/qrcode/check/ticket")).getAsJsonObject());
        client.fetchWaybillPage(query());
        client.markGenerated(42L);
        client.markPrinted(42L);
        client.markPrintFailed(42L);
        assertEquals(java.util.Collections.nCopies(4, "Bearer fixture-qr-token"), authorizations);
    }

    @Test void permissionRevocationClearsCredentialAndDoesNotRepeatTheWrite() throws Exception {
        client.login("fixture", "fixture-password");
        revoked.set(true);
        IOException failure = assertThrows(IOException.class, () -> client.markPrinted(42L));
        assertTrue(failure.getMessage().contains("权限已被收回"));
        assertNull(client.getUserId());
        assertEquals(1, authorizations.size());
        assertThrows(IllegalStateException.class, () -> client.fetchWaybillPage(query()));
        assertThrows(IllegalStateException.class, () -> client.markGenerated(42L));
        assertThrows(IllegalStateException.class, () -> client.markPrinted(42L));
        assertThrows(IllegalStateException.class, () -> client.markPrintFailed(42L));
        assertEquals(1, authorizations.size());
    }

    @Test void userIdAloneDoesNotEstablishAPrinterSession() {
        client.setUserId(9007199254741999L);
        assertThrows(IllegalStateException.class, () -> client.fetchWaybillPage(query()));
        assertTrue(authorizations.isEmpty());
    }

    @Test void sessionWithoutCredentialIsRejected() {
        assertThrows(IOException.class, () -> client.acceptSession(JsonParser.parseString("{\"userId\":42}").getAsJsonObject()));
        assertNull(client.getUserId());
    }

    @Test void incompleteNewLoginCannotReuseThePreviousSession() throws Exception {
        client.login("fixture", "fixture-password");
        assertThrows(IOException.class, () -> client.acceptSession(
                JsonParser.parseString("{\"userId\":43}").getAsJsonObject()));
        assertNull(client.getUserId());
        assertThrows(IllegalStateException.class, () -> client.fetchWaybillPage(query()));
        assertTrue(authorizations.isEmpty());
    }

    @Test void expiredCredentialReportsReloginAndFreshLoginRestoresRequests() throws Exception {
        client.login("fixture", "fixture-password");
        server.removeContext("/api/v1/waybills");
        server.createContext("/api/v1/waybills", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 401, "{\"message\":\"打印凭证无效或已过期\"}");
        });
        IOException failure = assertThrows(IOException.class, () -> client.fetchWaybillPage(query()));
        assertTrue(failure.getMessage().contains("关闭软件后重新登录"));
        assertNull(client.getUserId());
        assertThrows(IllegalStateException.class, () -> client.markPrinted(42L));
        assertEquals(1, authorizations.size());

        server.removeContext("/api/v1/waybills");
        server.createContext("/api/v1/waybills", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"items\":[],\"total\":0,\"page\":0,\"size\":20}");
        });
        assertTrue(client.login("fixture", "fixture-password"));
        assertEquals(0, client.fetchWaybillPage(query()).total());
        assertEquals(java.util.Collections.nCopies(2, "Bearer fixture-printer-token"), authorizations);
    }

    @Test void missingPrinterRoleReportsAuthorizationReasonInsteadOfPasswordFailure() throws Exception {
        server.removeContext("/api/v1/printer-accounts/login");
        server.createContext("/api/v1/printer-accounts/login", exchange -> respond(exchange, 403,
                "{\"message\":\"当前账户没有有效的打印或发货岗位\"}"));
        IOException failure = assertThrows(IOException.class, () -> client.login("fixture", "fixture-password"));
        assertTrue(failure.getMessage().contains("没有有效的打印或发货岗位"));
        assertNull(client.getUserId());
    }
}
