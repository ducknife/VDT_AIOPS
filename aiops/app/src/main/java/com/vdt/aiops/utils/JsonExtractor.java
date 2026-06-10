package com.vdt.aiops.utils;

/**
 * Extracts a JSON array from raw LLM output.
 *
 * <p>An LLM often surrounds its answer with noise — markdown fences,
 * explanatory preamble, or trailing remarks. This utility pulls out the
 * actual {@code [ ... ]} array so it can be deserialized cleanly.
 *
 * <p>Tolerates:
 * <ul>
 *   <li>Markdown code fences: {@code ```json ... ```}</li>
 *   <li>Prose that contains brackets, e.g. {@code [POSTGRES ERROR]}</li>
 *   <li>Brackets inside JSON string values, e.g. {@code "msg":"err [E01]"}</li>
 * </ul>
 */
public final class JsonExtractor {

    private JsonExtractor() {
        // Utility class — not instantiable.
    }

    /**
     * Returns the first well-formed JSON array found in {@code text},
     * or {@code "[]"} when none can be located.
     */
    public static String extractArray(String text) {
        if (text == null || text.isBlank()) {
            return "[]";
        }

        // 1. If the text is fenced, keep only what's inside the fence.
        String body = stripCodeFence(text);

        // 2. Locate where the real array of objects begins,
        //    skipping any bracketed prose before it.
        int open = findArrayStart(body);
        if (open < 0) {
            return "[]";
        }

        // 3. Walk the brackets (ignoring those inside strings)
        //    to find the matching closing bracket.
        return scanBalancedArray(body, open);
    }

    /** Returns the content inside the first {@code ```} fence, or the original text if unfenced. */
    private static String stripCodeFence(String text) {
        int fence = text.indexOf("```");
        if (fence < 0) {
            return text;
        }
        int start = text.indexOf('\n', fence);    // Skip the opening ```json line.
        int end = text.indexOf("```", start + 1); // Closing fence.
        return (start >= 0 && end > start) ? text.substring(start + 1, end) : text;
    }

    /**
     * Index of the '[' that opens an array of objects: a '[' whose next
     * non-whitespace char is '{' (objects) or ']' (empty array). This skips
     * prose brackets such as {@code [POSTGRES ERROR]}. Returns -1 if none.
     */
    private static int findArrayStart(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '[') {
                continue;
            }
            int next = i + 1;
            while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
                next++;
            }
            if (next < text.length() && (text.charAt(next) == '{' || text.charAt(next) == ']')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * From {@code open}, tracks bracket depth (ignoring brackets inside quoted
     * strings) and returns the substring up to the matching ']'. Returns
     * {@code "[]"} if the array never closes.
     */
    private static String scanBalancedArray(String text, int open) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                return text.substring(open, i + 1);
            }
        }
        return "[]";
    }
}
