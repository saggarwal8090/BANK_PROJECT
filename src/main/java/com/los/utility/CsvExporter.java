package com.los.utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Enterprise CSV Exporter utilizing standard BufferedWriter with proper field escaping.
 */
public class CsvExporter {

    private CsvExporter() {
        // Utility
    }

    /**
     * Exports rows to a CSV file on disk.
     */
    public static File exportToFile(File targetFile, String[] headers, List<String[]> rows) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            // Write Header
            writeLine(writer, headers);

            // Write Rows
            for (String[] row : rows) {
                writeLine(writer, row);
            }
            writer.flush();
        }
        return targetFile;
    }

    /**
     * Exports rows to a CSV string in memory.
     */
    public static String exportToString(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        appendCsvLine(sb, headers);
        for (String[] row : rows) {
            appendCsvLine(sb, row);
        }
        return sb.toString();
    }

    private static void writeLine(BufferedWriter writer, String[] values) throws IOException {
        StringBuilder sb = new StringBuilder();
        appendCsvLine(sb, values);
        writer.write(sb.toString());
    }

    private static void appendCsvLine(StringBuilder sb, String[] values) {
        for (int i = 0; i < values.length; i++) {
            String val = values[i] != null ? values[i] : "";
            // Escape double quotes and enclose in quotes if comma, newline or quote exists
            boolean needsQuotes = val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r");
            if (val.contains("\"")) {
                val = val.replace("\"", "\"\"");
            }
            if (needsQuotes) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
            if (i < values.length - 1) {
                sb.append(",");
            }
        }
        sb.append("\r\n");
    }
}
