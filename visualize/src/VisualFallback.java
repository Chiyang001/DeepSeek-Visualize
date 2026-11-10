package local.visualize;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualFallback {
    private static final VisualSpec.Status PARTIAL = VisualSpec.Status.PARTIAL;
    private static final VisualSpec.Status UNAVAILABLE = VisualSpec.Status.UNAVAILABLE;

    private VisualFallback() {}

    static VisualSpec recover(JSONObject original) {
        JSONArray items = original.optJSONArray("items");
        if (items == null || original.has("version") && !VisualSpec.same(original.opt("version"), 1)) {
            return VisualSpec.empty(UNAVAILABLE);
        }
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("title", field(original, "title"));
            root.put("subtitle", field(original, "subtitle"));
            String mode = original.optString("mode", "interactive");
            root.put("mode", "interactive".equals(mode) ? "interactive" : "static");
            List<JSONObject> recovered = new ArrayList<>();
            VisualSpec result = null;
            for (int i = 0; i < Math.min(16, items.length()); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                List<JSONObject> choices = new ArrayList<>();
                choices.add(item);
                choices.add(presentationDefaults(item, "static".equals(root.optString("mode"))));
                JSONObject readable = asText(item);
                if (readable != null) choices.add(readable);
                for (JSONObject choice : choices) {
                    JSONArray trial = new JSONArray(recovered).put(choice);
                    root.put("items", trial);
                    VisualSpec candidate = VisualSpec.validated(copy(root), PARTIAL);
                    if (candidate == null) continue;
                    recovered.add(choice);
                    result = candidate;
                    break;
                }
            }
            return result == null ? VisualSpec.empty(UNAVAILABLE) : result;
        } catch (JSONException invalid) {
            return VisualSpec.empty(UNAVAILABLE);
        }
    }

    private static JSONObject presentationDefaults(JSONObject item, boolean readOnly) throws JSONException {
        JSONObject copy = copy(item);
        copy.put("label", copy.opt("label") instanceof String ? field(copy, "label") : field(copy, "title"));
        switch (copy.optString("type")) {
            case "chart":
                copy.remove("highlight");
                break;
            case "table":
                copy.remove("emphasis");
                if (readOnly) copy.remove("editable");
                break;
            case "summary":
                if (!"rows".equals(copy.optString("layout"))) copy.remove("layout");
                break;
            default:
                break;
        }
        return copy;
    }

    private static JSONObject asText(JSONObject item) throws JSONException {
        StringBuilder body = new StringBuilder();
        for (String key : new String[]{"text", "detail", "description", "caption", "note", "alt"}) {
            addLine(body, field(item, key));
        }
        // Keep plain summary data and chart series even when their visual options are unsupported.
        switch (item.optString("type")) {
            case "summary":
                JSONArray metrics = item.optJSONArray("metrics");
                for (int i = 0; metrics != null && i < Math.min(6, metrics.length()); i++) {
                    JSONObject metric = metrics.optJSONObject(i);
                    if (metric == null) continue;
                    String value = scalarText(metric.opt("value"));
                    if (!value.isEmpty()) addLine(body, line(field(metric, "label"), value + field(metric, "unit")));
                    addLine(body, field(metric, "detail"));
                }
                break;
            case "chart":
                addSeries(body, item);
                break;
            case "emoji": case "gallery": case "facts": case "tiles": case "ranking": case "status":
                addEntries(body, item.optJSONArray("entries"));
                break;
            case "formula":
                addLine(body, field(item, "expression"));
                addLine(body, field(item, "result"));
                break;
            case "text": case "metric": case "progress":
                addLine(body, scalarText(item.opt("value")));
                break;
            default:
                break;
        }
        if (body.length() == 0) return null;
        String label = field(item, "label");
        if (label.isEmpty()) label = field(item, "title");
        String text = label.isEmpty() ? body.toString() : label + "\n" + body;
        return new JSONObject().put("type", "text").put("text", limit(text));
    }

    private static void addEntries(StringBuilder body, JSONArray entries) {
        for (int i = 0; entries != null && i < Math.min(8, entries.length()); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            String label = field(entry, "label");
            String value = scalarText(entry.opt("value"));
            addLine(body, value.isEmpty() ? label : line(label, value));
            for (String key : new String[]{"alt", "detail", "caption"}) addLine(body, field(entry, key));
        }
    }

    private static void addSeries(StringBuilder body, JSONObject item) {
        JSONArray labels = item.optJSONArray("labels");
        JSONArray series = item.optJSONArray("series");
        if (labels == null || series == null) return;
        for (int s = 0; s < Math.min(4, series.length()); s++) {
            JSONObject row = series.optJSONObject(s);
            JSONArray values = row == null ? null : row.optJSONArray("values");
            if (values == null || values.length() != labels.length()) continue;
            String name = field(row, "label");
            if (series.length() > 1) addLine(body, name);
            for (int i = 0; i < Math.min(12, labels.length()); i++) {
                String label = scalarText(labels.opt(i));
                String value = scalarText(values.opt(i));
                if (!label.isEmpty() && !value.isEmpty()) {
                    addLine(body, line(label, value + field(item, "unit")));
                }
            }
        }
    }

    private static JSONObject copy(JSONObject value) throws JSONException {
        return new JSONObject(value, keys(value));
    }

    private static String[] keys(JSONObject value) {
        List<String> keys = new ArrayList<>();
        value.keys().forEachRemaining(keys::add);
        return keys.toArray(new String[0]);
    }

    private static String field(JSONObject item, String name) {
        Object value = item.opt(name);
        return value instanceof String ? limit((String) value) : "";
    }

    private static String scalarText(Object value) {
        if (value instanceof String) return limit((String) value);
        if (value instanceof Number) {
            double n = ((Number) value).doubleValue();
            if (Double.isFinite(n) && Math.abs(n) <= 1e15) return value.toString();
        }
        return value instanceof Boolean ? value.toString() : "";
    }

    private static String limit(String text) {
        if (text.length() <= 480) return text;
        int end = Character.isHighSurrogate(text.charAt(478)) ? 478 : 479;
        return text.substring(0, end) + "…";
    }

    private static String line(String label, String value) {
        return label.isEmpty() ? value : label + " · " + value;
    }

    private static void addLine(StringBuilder body, String line) {
        if (line.isEmpty() || body.length() >= 480) return;
        if (body.length() > 0) body.append('\n');
        body.append(line);
    }
}
