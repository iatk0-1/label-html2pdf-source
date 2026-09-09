package com.label;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 简单的日志工具类
 *
 * 日志文件位置：
 * - Windows: %USERPROFILE%\.label-printer\logs\app.log
 * - 或当前目录下的 logs/app.log
 */
public class Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Path LOG_DIR;
    private static final Path LOG_FILE;
    private static final Object LOCK = new Object();

    static {
        // 尝试在用户目录下创建日志目录
        String userHome = System.getProperty("user.home");
        Path userLogDir = Paths.get(userHome, ".label-printer", "logs");
        Path logDir;

        try {
            Files.createDirectories(userLogDir);
            logDir = userLogDir;
        } catch (IOException e) {
            // 如果失败，使用当前目录
            System.err.println("无法在用户目录创建日志目录，使用当前目录: " + e.getMessage());
            try {
                Path currentLogDir = Paths.get("logs");
                Files.createDirectories(currentLogDir);
                logDir = currentLogDir;
            } catch (IOException ex) {
                throw new RuntimeException("无法创建日志目录", ex);
            }
        }

        LOG_DIR = logDir;
        LOG_FILE = LOG_DIR.resolve("app.log");

        // 启动时输出日志位置
        System.out.println("日志文件位置: " + LOG_FILE.toAbsolutePath());
    }

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * 记录 DEBUG 级别日志
     */
    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    /**
     * 记录 INFO 级别日志
     */
    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    /**
     * 记录 WARN 级别日志
     */
    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    /**
     * 记录 WARN 级别日志（带异常）
     */
    public static void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }

    /**
     * 记录 ERROR 级别日志
     */
    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    /**
     * 记录 ERROR 级别日志（带异常）
     */
    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    /**
     * 记录 HTTP 请求
     */
    public static void logHttpRequest(String method, String url, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP 请求: ").append(method).append(" ").append(url);
        if (body != null && !body.isEmpty()) {
            sb.append("\n  请求体: ").append(body);
        }
        info(sb.toString());
    }

    /**
     * 记录 HTTP 响应
     */
    public static void logHttpResponse(String method, String url, int statusCode, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP 响应: ").append(method).append(" ").append(url);
        sb.append("\n  状态码: ").append(statusCode);
        if (body != null && !body.isEmpty()) {
            // 限制响应体长度，避免日志过大
            String truncatedBody = body.length() > 500 ? body.substring(0, 500) + "... (truncated)" : body;
            sb.append("\n  响应体: ").append(truncatedBody);
        }
        info(sb.toString());
    }

    /**
     * 核心日志方法
     */
    private static void log(Level level, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String threadName = Thread.currentThread().getName();

        // 构建日志消息
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(timestamp)
                  .append(" [").append(threadName).append("]")
                  .append(" ").append(level.name())
                  .append(" - ").append(message);

        // 输出到控制台
        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(logMessage);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(logMessage);
        }

        // 写入日志文件
        synchronized (LOCK) {
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE.toFile(), true)))) {
                writer.println(logMessage);

                // 如果有异常，记录堆栈跟踪
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }

                writer.flush();
            } catch (IOException e) {
                System.err.println("写入日志文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取日志文件路径
     */
    public static Path getLogFile() {
        return LOG_FILE;
    }

    /**
     * 清空日志文件
     */
    public static void clearLog() {
        synchronized (LOCK) {
            try {
                Files.deleteIfExists(LOG_FILE);
                Files.createFile(LOG_FILE);
                info("日志文件已清空");
            } catch (IOException e) {
                error("清空日志文件失败", e);
            }
        }
    }
}
