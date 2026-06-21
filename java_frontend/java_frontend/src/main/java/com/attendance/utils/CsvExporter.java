package com.attendance.utils;

import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CsvExporter {
    public static void export(DefaultTableModel model, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            int cols = model.getColumnCount();
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append(',');
                sb.append(esc(model.getColumnName(c)));
            }
            pw.println(sb);
            for (int r = 0; r < model.getRowCount(); r++) {
                sb.setLength(0);
                for (int c = 0; c < cols; c++) {
                    if (c > 0) sb.append(',');
                    Object v = model.getValueAt(r, c);
                    sb.append(esc(v != null ? v.toString() : ""));
                }
                pw.println(sb);
            }
        }
    }

    private static String esc(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
