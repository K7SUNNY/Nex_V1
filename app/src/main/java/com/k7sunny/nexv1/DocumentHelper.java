package com.k7sunny.nexv1;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DocumentHelper {

    private static final String TAG = "DocumentHelper";
    public static final int MAX_DOCUMENT_CHARS = 1600; // Safe ~350-400 tokens max context window budget

    private static final String[] RESUME_KEYWORDS = {
        "DEVELOPER", "ENGINEER", "EXPERIENCE", "EDUCATION", "SKILLS",
        "PROJECTS", "SUMMARY", "REACT", "JAVASCRIPT", "PYTHON", "ANDROID",
        "SOFTWARE", "UNIVERSITY", "COLLEGE", "TECHNICAL", "MANAGEMENT",
        "FRAMEWORK", "DATABASE", "FRONTEND", "BACKEND"
    };

    public static class DocumentInfo {
        public final String name;
        public final String size;
        public final String content;
        public final String renderedImagePath;

        public DocumentInfo(String name, String size, String content, String renderedImagePath) {
            this.name = name;
            this.size = size;
            this.content = content;
            this.renderedImagePath = renderedImagePath;
        }

        public DocumentInfo(String name, String size, String content) {
            this(name, size, content, null);
        }
    }

    public static DocumentInfo parseDocument(Context context, Uri uri) {
        if (context == null || uri == null) return null;

        String fileName = getFileName(context, uri);
        String fileSize = getFileSize(context, uri);
        String content = null;
        String renderedImagePath = null;

        boolean isPdf = fileName != null && fileName.toLowerCase().endsWith(".pdf");

        if (isPdf) {
            // 1. Render first page to image for Vision model compatibility & visual analysis
            renderedImagePath = renderPdfPageToImage(context, uri);

            // 2. Extract text streams for Text models
            content = readPdfContent(context, uri, MAX_DOCUMENT_CHARS, fileName);
        } else {
            content = readTextContent(context, uri, MAX_DOCUMENT_CHARS);
        }

        if (content == null || content.trim().isEmpty()) {
            if (isPdf && renderedImagePath != null) {
                content = "[Document: " + fileName + " (Attached PDF document. Use Nex Vision for visual inspection).]";
            } else {
                return null;
            }
        }

        // Sanitize: strip out any null bytes or control characters that crash native tokenizers
        content = sanitizeText(content);

        return new DocumentInfo(fileName, fileSize, content, renderedImagePath);
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
     * Renders Page 0 of the PDF to a high-quality JPEG for Nex Vision multimodal analysis.
     */
    public static String renderPdfPageToImage(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) return null;

            PdfRenderer renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() == 0) {
                renderer.close();
                return null;
            }

            PdfRenderer.Page page = renderer.openPage(0);
            int origW = page.getWidth();
            int origH = page.getHeight();

            // Optimal resolution for mobile OCR / vision (aligned to 28px patches for Qwen2.5-VL)
            int targetDim = 784;
            float scale = Math.min((float) targetDim / origW, (float) targetDim / origH);
            if (scale <= 0) scale = 1.0f;
            int width = Math.max(28, (Math.round(origW * scale) / 28) * 28);
            int height = Math.max(28, (Math.round(origH * scale) / 28) * 28);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE); // Solid white background behind transparent PDF layers

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            renderer.close();

            File imagesDir = new File(context.getCacheDir(), "images");
            if (!imagesDir.exists()) imagesDir.mkdirs();

            File destFile = new File(imagesDir, "pdf_render_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(destFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bitmap.recycle();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "PdfRenderer could not render PDF page to image", e);
            return null;
        }
    }

    /**
     * Extracts text from PDF streams (handling FlateDecode, CMap / font subset encodings, and shifted glyphs).
     */
    private static String readPdfContent(Context context, Uri uri, int maxChars, String fileName) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int totalBytes = 0;
            int maxBytesToRead = 12 * 1024 * 1024; // 12MB max
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

        // 1. Parse all embedded /ToUnicode CMap tables in the PDF
        Map<Integer, String> cMap = parseAllCMaps(pdfBytes);

        // 2. Find all content stream blocks in PDF
        Pattern streamPattern = Pattern.compile("<<(.*?)>>\\s*stream[\\r\\n]+", Pattern.DOTALL);
        Matcher matcher = streamPattern.matcher(pdfString);

        while (matcher.find()) {
            if (textBuilder.length() >= maxChars * 2) break;

            String dict = matcher.group(1);
            // Skip image and non-text streams
            if (dict.contains("/Image") || dict.contains("/DCTDecode") || dict.contains("/JPXDecode") || dict.contains("/JBIG2Decode")) {
                continue;
            }

            int streamStart = matcher.end();
            int streamEnd = pdfString.indexOf("endstream", streamStart);
            if (streamEnd == -1) continue;

            byte[] streamBytes = new byte[streamEnd - streamStart];
            System.arraycopy(pdfBytes, streamStart, streamBytes, 0, streamBytes.length);

            String decompressed = null;
            if (dict.contains("/FlateDecode") || (!dict.contains("/Filter") && isZlibHeader(streamBytes))) {
                decompressed = decompressFlate(streamBytes);
            } else {
                decompressed = new String(streamBytes, StandardCharsets.ISO_8859_1);
            }

            if (decompressed != null && !decompressed.contains("begincmap")) {
                extractPdfTextTokens(decompressed, textBuilder, cMap, maxChars * 2);
            }
        }

        String result = textBuilder.toString().trim();

        // 3. Fallback: If structured BT...ET parsing found very little text, scan for readable word sequences
        if (result.length() < 30) {
            StringBuilder fallbackBuilder = new StringBuilder();
            extractFallbackTextFromPdf(pdfBytes, fallbackBuilder, maxChars * 2);
            String fallbackResult = fallbackBuilder.toString().trim();
            if (fallbackResult.length() > result.length()) {
                result = fallbackResult;
            }
        }

        if (result.isEmpty()) {
            return "[Document: " + fileName + " (Scanned or image-based PDF without selectable text layers. Use Nex Vision for visual inspection).]";
        }

        // 4. Auto-detect font-subset shift encoding and normalize to clean English
        result = normalizeShiftedFontText(result);

        if (result.length() > maxChars) {
            result = result.substring(0, maxChars) + "\n\n[... Truncated to fit context window ...]";
        }

        return result;
    }

    private static Map<Integer, String> parseAllCMaps(byte[] pdfBytes) {
        Map<Integer, String> cMap = new HashMap<>();
        String pdfIso = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Pattern streamPattern = Pattern.compile("<<(.*?)>>\\s*stream[\\r\\n]+", Pattern.DOTALL);
        Matcher matcher = streamPattern.matcher(pdfIso);

        while (matcher.find()) {
            String dict = matcher.group(1);
            int streamStart = matcher.end();
            int streamEnd = pdfIso.indexOf("endstream", streamStart);
            if (streamEnd == -1) continue;

            byte[] streamBytes = new byte[streamEnd - streamStart];
            System.arraycopy(pdfBytes, streamStart, streamBytes, 0, streamBytes.length);

            String decompressed = null;
            if (dict.contains("/FlateDecode") || isZlibHeader(streamBytes)) {
                decompressed = decompressFlate(streamBytes);
            } else {
                decompressed = new String(streamBytes, StandardCharsets.ISO_8859_1);
            }

            if (decompressed != null && (decompressed.contains("begincmap") || decompressed.contains("beginbfchar") || decompressed.contains("beginbfrange"))) {
                parseCMapStream(decompressed, cMap);
            }
        }
        return cMap;
    }

    private static void parseCMapStream(String cmapText, Map<Integer, String> cMap) {
        // Parse beginbfchar ... endbfchar
        Pattern bfcharSection = Pattern.compile("beginbfchar(.*?)endbfchar", Pattern.DOTALL);
        Matcher mSection = bfcharSection.matcher(cmapText);
        while (mSection.find()) {
            String content = mSection.group(1);
            Pattern linePattern = Pattern.compile("<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>");
            Matcher mLine = linePattern.matcher(content);
            while (mLine.find()) {
                try {
                    int src = Integer.parseInt(mLine.group(1), 16);
                    String dstHex = mLine.group(2);
                    String dstChar = decodePdfHexString(dstHex, null);
                    if (!dstChar.isEmpty()) {
                        cMap.put(src, dstChar);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Parse beginbfrange ... endbfrange
        Pattern bfrangeSection = Pattern.compile("beginbfrange(.*?)endbfrange", Pattern.DOTALL);
        Matcher mRangeSection = bfrangeSection.matcher(cmapText);
        while (mRangeSection.find()) {
            String content = mRangeSection.group(1);
            Pattern r1 = Pattern.compile("<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>");
            Matcher mR1 = r1.matcher(content);
            while (mR1.find()) {
                try {
                    int start = Integer.parseInt(mR1.group(1), 16);
                    int end = Integer.parseInt(mR1.group(2), 16);
                    int dstStart = Integer.parseInt(mR1.group(3), 16);
                    for (int s = start; s <= end; s++) {
                        cMap.put(s, String.valueOf((char) (dstStart + (s - start))));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static boolean isZlibHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return false;
        return (bytes[0] == 0x78) && (bytes[1] == (byte) 0x9C || bytes[1] == (byte) 0x01 || bytes[1] == (byte) 0xDA || bytes[1] == 0x5E);
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
            try {
                Inflater inflater = new Inflater(true); // nowrap=true fallback
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

    private static void extractPdfTextTokens(String streamText, StringBuilder output, Map<Integer, String> cMap, int maxChars) {
        // Extract text between BT and ET operators
        Pattern btPattern = Pattern.compile("BT\\s+(.*?)\\s+ET", Pattern.DOTALL);
        Matcher btMatcher = btPattern.matcher(streamText);

        boolean foundBt = false;
        while (btMatcher.find()) {
            foundBt = true;
            if (output.length() >= maxChars) break;
            String textBlock = btMatcher.group(1);
            parseTextBlock(textBlock, output, cMap, maxChars);
        }

        // If no BT...ET blocks found, parse text operations directly in stream
        if (!foundBt) {
            parseTextBlock(streamText, output, cMap, maxChars);
        }
    }

    private static void parseTextBlock(String textBlock, StringBuilder output, Map<Integer, String> cMap, int maxChars) {
        // Match string literals: (Hello) Tj, <00480069> Tj, or [(Hello) 10 <0069>] TJ
        Pattern opPattern = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)\\s*(?:Tj|'|\")|<([0-9a-fA-F]+)>\\s*(?:Tj|'|\")|\\[(.*?)\\]\\s*TJ", Pattern.DOTALL);
        Matcher opMatcher = opPattern.matcher(textBlock);

        while (opMatcher.find()) {
            if (output.length() >= maxChars) break;

            if (opMatcher.group(1) != null) {
                String raw = decodePdfString(opMatcher.group(1), cMap);
                if (!raw.trim().isEmpty()) {
                    output.append(raw).append(" ");
                }
            } else if (opMatcher.group(2) != null) {
                String hex = decodePdfHexString(opMatcher.group(2), cMap);
                if (!hex.trim().isEmpty()) {
                    output.append(hex).append(" ");
                }
            } else if (opMatcher.group(3) != null) {
                String arrayContent = opMatcher.group(3);
                Pattern subPattern = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)|<([0-9a-fA-F]+)>");
                Matcher subMatcher = subPattern.matcher(arrayContent);
                while (subMatcher.find()) {
                    if (subMatcher.group(1) != null) {
                        String raw = decodePdfString(subMatcher.group(1), cMap);
                        if (!raw.trim().isEmpty()) {
                            output.append(raw);
                        }
                    } else if (subMatcher.group(2) != null) {
                        String hex = decodePdfHexString(subMatcher.group(2), cMap);
                        if (!hex.trim().isEmpty()) {
                            output.append(hex);
                        }
                    }
                }
                output.append(" ");
            }
        }
        output.append("\n");
    }

    private static String decodePdfString(String str, Map<Integer, String> cMap) {
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
                            if (cMap != null && cMap.containsKey(octal)) {
                                sb.append(cMap.get(octal));
                            } else {
                                sb.append((char) octal);
                            }
                        } else {
                            if (cMap != null && cMap.containsKey((int) next)) {
                                sb.append(cMap.get((int) next));
                            } else {
                                sb.append(next);
                            }
                        }
                        break;
                }
            } else {
                int code = (int) c;
                if (cMap != null && cMap.containsKey(code)) {
                    sb.append(cMap.get(code));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String decodePdfHexString(String hex, Map<Integer, String> cMap) {
        if (hex == null || hex.isEmpty()) return "";
        if (hex.length() % 2 != 0) {
            hex = hex + "0";
        }

        // 1. If CMap exists, map individual hex codes
        if (cMap != null && !cMap.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (hex.length() >= 4 && hex.length() % 4 == 0) {
                for (int i = 0; i < hex.length(); i += 4) {
                    int code = Integer.parseInt(hex.substring(i, i + 4), 16);
                    if (cMap.containsKey(code)) {
                        sb.append(cMap.get(code));
                    } else if (cMap.containsKey(code & 0xFF)) {
                        sb.append(cMap.get(code & 0xFF));
                    } else {
                        sb.append((char) (code & 0xFF));
                    }
                }
                return sb.toString();
            } else {
                for (int i = 0; i < hex.length(); i += 2) {
                    int code = Integer.parseInt(hex.substring(i, i + 2), 16);
                    if (cMap.containsKey(code)) {
                        sb.append(cMap.get(code));
                    } else {
                        sb.append((char) code);
                    }
                }
                return sb.toString();
            }
        }

        // 2. Check if UTF-16BE
        if (hex.length() >= 4 && hex.length() % 4 == 0) {
            boolean isTwoByte = true;
            for (int i = 0; i < hex.length(); i += 4) {
                int high = Integer.parseInt(hex.substring(i, i + 2), 16);
                if (high != 0 && !(i == 0 && hex.startsWith("FEFF"))) {
                    isTwoByte = false;
                    break;
                }
            }
            if (isTwoByte) {
                StringBuilder sb = new StringBuilder();
                int start = hex.startsWith("FEFF") ? 4 : 0;
                for (int i = start; i < hex.length(); i += 4) {
                    int charCode = Integer.parseInt(hex.substring(i, i + 4), 16);
                    if (charCode >= 32 && charCode < 127) {
                        sb.append((char) charCode);
                    } else if (charCode == 10 || charCode == 13 || charCode == 9) {
                        sb.append((char) charCode);
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
        }

        // 3. Standard ASCII hex
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            try {
                int val = Integer.parseInt(hex.substring(i, i + 2), 16);
                if (val >= 32 && val <= 126) {
                    sb.append((char) val);
                } else if (val == 10 || val == 13 || val == 9) {
                    sb.append((char) val);
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private static void extractFallbackTextFromPdf(byte[] pdfBytes, StringBuilder output, int maxChars) {
        String pdfIso = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        Pattern streamPattern = Pattern.compile("stream[\\r\\n]+(.*?)endstream", Pattern.DOTALL);
        Matcher matcher = streamPattern.matcher(pdfIso);

        while (matcher.find()) {
            if (output.length() >= maxChars) break;
            String rawStream = matcher.group(1);
            byte[] bytes = rawStream.getBytes(StandardCharsets.ISO_8859_1);
            String decompressed = decompressFlate(bytes);
            if (decompressed == null) decompressed = rawStream;

            Pattern wordPattern = Pattern.compile("[A-Za-z0-9@.,:;()\\-_/]{3,}");
            Matcher wordMatcher = wordPattern.matcher(decompressed);
            int wordCount = 0;
            while (wordMatcher.find()) {
                if (output.length() >= maxChars) break;
                String word = wordMatcher.group();
                if (!word.equals("obj") && !word.equals("endobj") && !word.equals("FlateDecode") && !word.equals("stream") && !word.equals("endstream")) {
                    output.append(word).append(" ");
                    wordCount++;
                    if (wordCount % 12 == 0) {
                        output.append("\n");
                    }
                }
            }
        }
    }

    /**
     * Detects if the extracted text has a Caesar-shifted font subset encoding (e.g. +1 shift in PDF fonts)
     * and normalizes it to clean, readable English words.
     */
    private static String normalizeShiftedFontText(String rawText) {
        if (rawText == null || rawText.length() < 20) return rawText;

        int baseScore = countKeywordMatches(rawText);
        int bestScore = baseScore;
        String bestText = rawText;

        // Test shifts from -3 to +3
        for (int shift = -3; shift <= 3; shift++) {
            if (shift == 0) continue;
            StringBuilder shifted = new StringBuilder(rawText.length());
            for (int i = 0; i < rawText.length(); i++) {
                char c = rawText.charAt(i);
                if (c >= 33 && c <= 126) {
                    char sc = (char) (c + shift);
                    shifted.append(sc);
                } else {
                    shifted.append(c);
                }
            }
            String shiftedStr = shifted.toString();
            int score = countKeywordMatches(shiftedStr);
            if (score > bestScore + 2) {
                bestScore = score;
                bestText = shiftedStr;
            }
        }

        return cleanExtractedText(bestText);
    }

    private static int countKeywordMatches(String text) {
        String upper = text.toUpperCase(java.util.Locale.US);
        int count = 0;
        for (String kw : RESUME_KEYWORDS) {
            if (upper.contains(kw)) count++;
        }
        return count;
    }

    private static String cleanExtractedText(String text) {
        return text.replace("t  t", "•")
                   .replace(" t ", " • ")
                   .replaceAll("[ \\t]+", " ")
                   .replaceAll("(?m)^\\s+$", "")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();
    }

    private static String sanitizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
