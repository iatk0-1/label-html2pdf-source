package com.label;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PdfPrinter {

    public static List<String> getAvailablePrinters() {
        FutureTask<List<String>> lookupTask = new FutureTask<>(() -> {
            List<String> printers = new ArrayList<>();
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            for (PrintService service : services) {
                printers.add(service.getName());
            }
            return printers;
        });

        Thread lookupThread = new Thread(lookupTask, "printer-lookup");
        lookupThread.setDaemon(true);
        lookupThread.start();

        try {
            return lookupTask.get(8, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            lookupTask.cancel(true);
            throw new IllegalStateException("读取打印机超时，请检查 Windows 打印后台服务或网络打印机", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取打印机被中断", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("读取打印机失败: " + cause.getMessage(), cause);
        }
    }

    public static void printPdf(File pdfFile, String printerName) throws Exception {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService targetService = null;

        for (PrintService service : services) {
            if (service.getName().equals(printerName)) {
                targetService = service;
                break;
            }
        }

        if (targetService == null) {
            throw new Exception("打印机未找到: " + printerName);
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(targetService);
            job.setPageable(new PDFPageable(document));

            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new Copies(1));
            attrs.add(MediaSizeName.ISO_A4);
            attrs.add(Sides.ONE_SIDED);

            job.print(attrs);
        }
    }
}
