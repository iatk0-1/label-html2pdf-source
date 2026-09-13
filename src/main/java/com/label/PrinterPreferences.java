package com.label;

import java.util.prefs.Preferences;

/** Stores the last selected printer for the current user. */
public final class PrinterPreferences {
    private static final String LAST_PRINTER_KEY = "lastPrinter";
    private static final Preferences PREFS = Preferences.userNodeForPackage(PrinterPreferences.class);

    private PrinterPreferences() {
    }

    public static String getLastPrinter() {
        return PREFS.get(LAST_PRINTER_KEY, "");
    }

    public static void setLastPrinter(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            PREFS.remove(LAST_PRINTER_KEY);
        } else {
            PREFS.put(LAST_PRINTER_KEY, printerName);
        }
    }
}
