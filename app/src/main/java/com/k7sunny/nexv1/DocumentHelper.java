package com.k7sunny.nexv1;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DocumentHelper {

    private static final String TAG = "DocumentHelper";
    public static final int MAX_DOCUMENT_CHARS = 3500; // ~800 tokens max for safe context window

    public static class DocumentInfo {
        public final String name;
        public final String size;
        public final String content;

        public DocumentInfo(String name, String size, String content) {
            this.name = name;
            this.size = size;
            this.content = content;
        }
    }

    public static DocumentInfo parseDocument(Context context, Uri uri) {
        if (context == null || uri == null) return null;

        String fileName = getFileName(context, uri);
        String fileSize = getFileSize(context, uri);
        String content;

        boolean isPdf = fileName != null && fileName.toLowerCase().endsWith(".pdf");

        if (isPdf) {
            content = readPdfContent(context, uri, MAX_DOCUMENT_CHARS, fileName);
        } else {
            content = readTextContent(context, uri, MAX_DOCUMENT_CHARS);
        }

        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // Sanitize: strip out any null bytes or control characters that crash native tokenizers
        content = sanitizeText(content);

        return new DocumentInfo(fileName, fileSize, content);
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get filename from cursor", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "document.txt";
    }

    public static String getFileSize(Context context, Uri uri) {
        long bytes = 0;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        bytes = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get file size from cursor", e);
            }
        }
        if (bytes <= 0) {
            return "";
        } else if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private static String readTextContent(Context context, Uri uri, int maxChars) {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                int totalChars = 0;
                while ((read = reader.read(buffer)) != -1) {
                    if (totalChars + read > maxChars) {
                        int remaining = maxChars - totalChars;
                        if (remaining > 0) {
                            builder.append(buffer, 0, remaining);
                        }
                        builder.append("\n\n[... Document truncated to fit memory context window ...]");
                        break;
                    } else {
                        builder.append(buffer, 0, read);
                        totalChars += read;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read document text", e);
            return null;
        }

        return builder.toString();
    }

    /**
     * Extracts text from PDF files without dumping raw binary/image streams.
     * Decompresses FlateDecode text streams and extracts BT ... ET text blocks.
     */
    private static String readPdfContent(Context context, Uri uri, int maxChars, String fileName) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int totalBytes = 0;
            int maxBytesToRead = 10 * 1024 * 1024; // 10MB max
            while ((read = in.read(buf)) != -1 && totalBytes < maxBytesToRead) {
                baos.write(buf, 0, read);
                totalBytes += read;
            }
            byte[] pdfBytes = baos.toByteArray();
            return extractTextFromPdfBytes(pdfBytes, maxChars, fileName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read PDF document", e);
            return "[Error reading PDF document: " + e.getMessage() + "]";
        }
    }

    private static String extractTextFromPdfBytes(byte[] pdfBytes, int maxChars, String fileName) {
        StringBuilder textBuilder = new StringBuilder();
        String pdfString = new String(pdfBytes, StandardCharsets.ISO_8859_1);

        // Find stream blocks in PDF
        Pattern streamPattern = Pattern.compile("<<(.*?)>>\\s*stream\\r?\\n", Pattern.DOTALL);
        Matcher matcher = streamPattern.matcher(pdfString);

        while (matcher.find()) {
            if (textBuilder.length() >= maxChars) break;

            String dict = matcher.group(1);
            // Skip image and non-text streams
            if (dict.contains("/Image") || dict.contains("/DCTDecode") || dict.contains("/JPXDecode")) {
                continue;
            }

            int streamStart = matcher.end();
            int streamEnd = pdfString.indexOf("endstream", streamStart);
            if (streamEnd == -1) continue;

            byte[] streamBytes = new byte[streamEnd - streamStart];
            System.arraycopy(pdfBytes, streamStart, streamBytes, 0, streamBytes.length);

            String decompressed = null;
            if (dict.contains("/FlateDecode")) {
                decompressed = decompressFlate(streamBytes);
            } else {
                decompressed = new String(streamBytes, StandardCharsets.ISO_8859_1);
            }

            if (decompressed != null) {
                extractPdfTextTokens(decompressed, textBuilder, maxChars);
            }
        }

        String result = textBuilder.toString().trim();
        if (result.isEmpty()) {
            return "[Document: " + fileName + " (Scanned or image-based PDF without selectable text layers. Use Nex Vision with an image/screenshot for visual analysis).]";
        }

        if (result.length() > maxChars) {
            result = result.substring(0, maxChars) + "\n\n[... Truncated to fit context window ...]";
        }

        return result;
    }

    private static String decompressFlate(byte[] compressed) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            InflaterInputStream iis = new InflaterInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = iis.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toString("ISO-8859-1");
        } catch (Exception e) {
            // Try raw inflater with nowrap=true fallback
            try {
                Inflater inflater = new Inflater(true);
                inflater.setInput(compressed);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                while (!inflater.finished()) {
                    int count = inflater.inflate(buf);
                    if (count == 0 && inflater.needsInput()) break;
                    baos.write(buf, 0, count);
                }
                inflater.end();
                return baos.toString("ISO-8859-1");
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void extractPdfTextTokens(String streamText, StringBuilder output, int maxChars) {
        // Extract text between BT and ET operators
        Pattern btPattern = Pattern.compile("BT\\s+(.*?)\\s+ET", Pattern.DOTALL);
        Matcher btMatcher = btPattern.matcher(streamText);

        while (btMatcher.find()) {
            if (output.length() >= maxChars) break;
            String textBlock = btMatcher.group(1);

            // Match string literals: (Hello) Tj, (World) ', or [(Hello) 10 (World)] TJ
            Pattern strPattern = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)\\s*(?:Tj|'|\")|\\[(.*?)\\]\\s*TJ", Pattern.DOTALL);
            Matcher strMatcher = strPattern.matcher(textBlock);

            while (strMatcher.find()) {
                if (output.length() >= maxChars) break;

                if (strMatcher.group(1) != null) {
                    String raw = decodePdfString(strMatcher.group(1));
                    if (!raw.trim().isEmpty()) {
                        output.append(raw).append(" ");
                    }
                } else if (strMatcher.group(2) != null) {
                    String arrayContent = strMatcher.group(2);
                    Pattern arrayStrPattern = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)");
                    Matcher arrayMatcher = arrayStrPattern.matcher(arrayContent);
                    while (arrayMatcher.find()) {
                        String raw = decodePdfString(arrayMatcher.group(1));
                        if (!raw.trim().isEmpty()) {
                            output.append(raw).append(" ");
                        }
                    }
                }
            }
            output.append("\n");
        }
    }

    private static String decodePdfString(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = str.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '(': sb.append('('); break;
                    case ')': sb.append(')'); break;
                    case '\\': sb.append('\\'); break;
                    default:
                        if (next >= '0' && next <= '7') {
                            int octal = next - '0';
                            if (i + 1 < len && str.charAt(i + 1) >= '0' && str.charAt(i + 1) <= '7') {
                                octal = octal * 8 + (str.charAt(++i) - '0');
                                if (i + 1 < len && str.charAt(i + 1) >= '0' && str.charAt(i + 1) <= '7') {
                                    octal = octal * 8 + (str.charAt(++i) - '0');
                                }
                            }
                            sb.append((char) octal);
                        } else {
                            sb.append(next);
                        }
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String sanitizeText(String text) {
        if (text == null) return "";
        // Remove null bytes, non-printable control characters below ASCII 32 except \n, \r, \t
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
