package com.k7sunny.nexv1;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    // Colors matching IntelliJ/Android Studio dark theme
    private static final int COLOR_KEYWORD = Color.parseColor("#FF7B72"); // Light red / pink
    private static final int COLOR_STRING = Color.parseColor("#A5D6FF");  // Light blue
    private static final int COLOR_COMMENT = Color.parseColor("#8B949E"); // Muted grey
    private static final int COLOR_NUMBER = Color.parseColor("#D2A8FF");  // Purple
    private static final int COLOR_ANNOTATION = Color.parseColor("#FFD580"); // Orange
    private static final int COLOR_CLASS_TYPE = Color.parseColor("#FFA657"); // Orange/Yellow

    // Java/Kotlin/C++ Keywords
    private static final String[] KEYWORDS_JVM = {
        "package", "import", "class", "interface", "fun", "val", "var", "void", "public", "private", 
        "protected", "return", "if", "else", "for", "while", "do", "break", "continue", "new", 
        "this", "super", "null", "true", "false", "throw", "try", "catch", "finally", "class", 
        "const", "static", "final", "abstract", "extends", "implements", "native", "volatile", 
        "synchronized", "transient", "override", "as", "is", "in", "when", "object",
        "typeof", "let", "const", "def", "elif", "and", "or", "not", "with", "from",
        "lambda"
    };

    public static Spannable formatCode(String code, String language) {
        if (code == null) return new SpannableStringBuilder("");
        
        SpannableStringBuilder builder = new SpannableStringBuilder(code);
        String lang = (language != null) ? language.toLowerCase() : "code";

        try {
            // 1. Highlight Numbers
            highlightPattern(builder, Pattern.compile("\\b\\d+\\b"), COLOR_NUMBER);

            // 2. Highlight XML / HTML tags
            if (lang.equals("xml") || lang.equals("html") || lang.equals("svg") || lang.equals("xhtml")) {
                highlightPattern(builder, Pattern.compile("(?<=<|</)[a-zA-Z0-9_.-]+"), COLOR_KEYWORD);
                highlightPattern(builder, Pattern.compile("[a-zA-Z0-9_.:-]+(?=\\s*=)"), COLOR_CLASS_TYPE);
                highlightPattern(builder, Pattern.compile("</?|/?>"), COLOR_COMMENT);
            } else {
                // 3. Highlight Keywords
                for (String word : KEYWORDS_JVM) {
                    Pattern pattern = Pattern.compile("\\b" + word + "\\b");
                    highlightPattern(builder, pattern, COLOR_KEYWORD);
                }

                // 4. Highlight Annotations
                highlightPattern(builder, Pattern.compile("@[a-zA-Z0-9_]+"), COLOR_ANNOTATION);
            }

            // 5. Highlight Strings (overrides keywords/numbers inside string literals)
            highlightPattern(builder, Pattern.compile("\"[^\"]*\""), COLOR_STRING);
            highlightPattern(builder, Pattern.compile("'[^']*'"), COLOR_STRING);

            // 6. Highlight Comments (highest priority, overrides everything)
            highlightPattern(builder, Pattern.compile("//.*"), COLOR_COMMENT);
            highlightPattern(builder, Pattern.compile("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/"), COLOR_COMMENT);
            if (lang.equals("python") || lang.equals("py")) {
                highlightPattern(builder, Pattern.compile("#.*"), COLOR_COMMENT);
            }

        } catch (Exception e) {
            // Fallback to plain text if regex fails
        }

        return builder;
    }

    private static void highlightPattern(SpannableStringBuilder builder, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(builder);
        while (matcher.find()) {
            builder.setSpan(
                new ForegroundColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}
