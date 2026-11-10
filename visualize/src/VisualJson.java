package local.visualize;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

final class VisualJson {
    private static final Pattern EARLY_CARD_CLOSE =
            Pattern.compile("(\\}\\s*\\]\\s*\\})\\s*,\\s*\\{\\s*\"type\"\\s*:");

    private VisualJson() {}

    static JSONObject read(String source) {
        if (source == null || source.length() > 24000) return null;
        try {
            StringBuilder result = new StringBuilder(source.length());
            normalize(source, result, null);
            return decode(result.toString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static JSONObject readPrefix(String source) {
        if (source == null || source.length() > 24000) return null;
        StringBuilder result = new StringBuilder(source.length());
        List<Integer> checkpoints = new ArrayList<>();
        try {
            normalize(source, result, checkpoints);
        } catch (IllegalArgumentException invalid) {
            // The checkpoints only include complete objects from the top-level items array.
        }
        for (int i = checkpoints.size() - 1; i >= 0; i--) {
            JSONObject prefix = decode(result.substring(0, checkpoints.get(i)) + "]}");
            if (prefix != null) return prefix;
        }
        return null;
    }

    private static JSONObject decode(String source) {
        try {
            JSONTokener json = new JSONTokener(source);
            Object root = json.nextValue();
            return root instanceof JSONObject && json.nextClean() == 0 ? (JSONObject) root : null;
        } catch (JSONException invalid) {
            return null;
        }
    }

    private static void normalize(String source, StringBuilder result, List<Integer> checkpoints) {
        // Bound nesting before handing untrusted output to Android's recursive JSON parser.
        char[] closes = new char[24];
        int depth = 0, lastToken = -1, quoteStart = -1;
        boolean quoted = false, escaped = false, items = false, itemsKey = false;
        Matcher earlyClose = EARLY_CARD_CLOSE.matcher(source);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                VisualSpec.require(c >= ' ');
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') {
                    quoted = false;
                    lastToken = result.length();
                    itemsKey = depth == 1 && i - quoteStart == 6
                            && source.regionMatches(quoteStart, "\"items\"", 0, 7);
                }
            } else if (c == '"') {
                quoted = true;
                quoteStart = i;
            } else if (" \n\r\t".indexOf(c) < 0) {
                char previous = lastToken < 0 ? '\0' : result.charAt(lastToken);
                if (c == '{' || c == '[') {
                    VisualSpec.require(depth < closes.length);
                    if (depth == 1) items = c == '[' && previous == ':' && itemsKey;
                    closes[depth++] = c == '{' ? '}' : ']';
                } else if (c == '}' || c == ']') {
                    // A nested array ended with an early card close before another component.
                    if (c == '}' && previous == ']' && items && depth == 4
                            && closes[2] == '}' && closes[3] == ']'
                            && earlyClose.region(i, source.length()).lookingAt()) {
                        result.append(']');
                        depth--;
                        i = earlyClose.end(1) - 1;
                    }
                    VisualSpec.require(depth > 0 && closes[--depth] == c);
                    if (previous == ',') result.setLength(lastToken);
                    if (checkpoints != null && items && c == '}' && depth == 2
                            && checkpoints.size() < 16) checkpoints.add(result.length() + 1);
                    if (depth < 2) items = false;
                } else {
                    VisualSpec.require(",:0123456789.eE-+truefalsn".indexOf(c) >= 0);
                    if (c == ',') VisualSpec.require(previous != ',' && previous != '[' && previous != '{');
                }
                lastToken = result.length();
            }
            result.append(c);
        }
        VisualSpec.require(!quoted && depth == 0);
    }
}
