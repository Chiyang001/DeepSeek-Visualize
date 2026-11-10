package local.visualize;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class VisualSpec {
    enum Status { READY, PARTIAL, INCOMPLETE, UNAVAILABLE }

    final JSONObject root;
    final boolean readOnly;
    final Map<String, JSONObject> controls = new HashMap<>();
    final Status status;

    private VisualSpec(JSONObject root, boolean readOnly, Status status) {
        this.root = root;
        this.readOnly = readOnly;
        this.status = status;
    }

    public static VisualSpec parse(String language, String source) {
        if (!"visualize".equalsIgnoreCase(language)) return null;
        JSONObject root = VisualJson.read(source);
        return root == null ? null : validated(root, Status.READY);
    }

    public static VisualSpec forReply(String language, String source, boolean closed) {
        if (!"visualize".equalsIgnoreCase(language)) return null;
        if (!closed) return empty(Status.INCOMPLETE);
        JSONObject root = VisualJson.read(source);
        if (root == null) {
            JSONObject prefix = VisualJson.readPrefix(source);
            return prefix == null ? empty(Status.UNAVAILABLE) : VisualFallback.recover(prefix);
        }
        VisualSpec spec = validated(root, Status.READY);
        return spec == null ? VisualFallback.recover(root) : spec;
    }

    static VisualSpec empty(Status status) {
        return new VisualSpec(new JSONObject(), true, status);
    }

    static VisualSpec validated(JSONObject root, Status status) {
        try {
            require(same(root.get("version"), 1));
            string(root.get("title"));
            if (root.has("subtitle")) string(root.get("subtitle"));
            String mode = root.has("mode") ? string(root.get("mode")) : "interactive";
            require("static".equals(mode) || "interactive".equals(mode));
            VisualSpec spec = new VisualSpec(root, "static".equals(mode), status);
            List<JSONObject> items = new ArrayList<>();
            collect(root.get("items"), 0, items);
            int quizCount = 0;
            for (JSONObject item : items) {
                String type = item.getString("type");
                if (spec.readOnly) require(isReadOnlyItem(type, item));
                switch (type) {
                    case "slider":
                    case "select":
                    case "toggle":
                    case "input":
                    case "checklist":
                        String id = string(item.get("id"));
                        require(id.matches("[A-Za-z_][A-Za-z0-9_]{0,31}"));
                        require(spec.controls.put(id, item) == null);
                        require(accepts(item, item.get("initial")));
                        break;
                    case "quiz":
                        require(++quizCount == 1);
                        break;
                    default:
                        break;
                }
            }
            for (JSONObject item : items) spec.validate(item);
            return spec;
        } catch (JSONException | IllegalArgumentException | ClassCastException invalid) {
            return null;
        }
    }

    private static boolean isReadOnlyItem(String type, JSONObject item) {
        switch (type) {
            case "text": case "metric": case "summary": case "progress": case "chart":
            case "map": case "diagram": case "gantt":
            case "emoji": case "image": case "gallery":
            case "callout": case "quote": case "facts": case "tiles": case "ranking": case "status": case "rating":
            case "palette": case "board": case "diff":
            case "calendar": case "tree": case "formula":
                return true;
            case "table":
                return !item.has("editable");
            case "deck":
                return "timeline".equals(item.optString("kind")) || "steps".equals(item.optString("kind"));
            default:
                return false;
        }
    }

    private static void collect(Object source, int depth, List<JSONObject> all) throws JSONException {
        require(depth <= 2);
        JSONArray items = array(source, 1, 16);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            require(all.size() < 32);
            all.add(item);
            if ("group".equals(item.getString("type"))) {
                String mode = item.getString("mode");
                require("tabs".equals(mode) || "accordion".equals(mode));
                JSONArray pages = array(item.get("pages"), 1, 6);
                for (int j = 0; j < pages.length(); j++) {
                    JSONObject page = pages.getJSONObject(j);
                    string(page.get("label"));
                    collect(page.get("items"), depth + 1, all);
                }
            }
        }
    }

    private void validate(JSONObject item) throws JSONException {
        String type = item.getString("type");
        if (!"text".equals(type) && !"quiz".equals(type)) string(item.get("label"));
        if (item.has("unit")) string(item.get("unit"));
        switch (type) {
            case "text":
                if (item.has("value")) expression(item.get("value"), 0);
                else string(item.get("text"));
                break;
            case "slider":
            case "toggle":
            case "group":
                break;
            case "input":
                VisualInputs.validate(item);
                break;
            case "select":
            case "checklist":
                JSONArray options = array(item.get("options"), 1, 12);
                for (int i = 0; i < options.length(); i++) {
                    JSONObject option = options.getJSONObject(i);
                    string(option.get("label"));
                    scalar(option.get("value"));
                    for (int j = 0; j < i; j++) {
                        require(!same(options.getJSONObject(j).get("value"), option.get("value")));
                    }
                }
                break;
            case "metric":
                if (item.has("digits")) {
                    double digits = number(item.get("digits"));
                    require(digits >= 0 && digits <= 4 && digits == Math.rint(digits));
                }
                expression(item.get("value"), 0);
                break;
            case "button":
                String action = item.getString("action");
                if ("reset".equals(action)) break;
                require("set".equals(action) && controls.containsKey(item.getString("target")));
                expression(item.get("value"), 0);
                break;
            case "chart":
                VisualChart.validate(this, item);
                break;
            case "quiz":
                VisualQuiz.validate(item);
                break;
            case "table":
                VisualTable.validate(item);
                if (item.has("emphasis")) {
                    int count = item.getJSONArray("columns").length();
                    JSONArray emphasis = array(item.get("emphasis"), 0, count);
                    for (int i = 0; i < emphasis.length(); i++) {
                        double column = number(emphasis.get(i));
                        require(column >= 0 && column < count && column == Math.rint(column));
                    }
                }
                break;
            case "summary":
                VisualStatic.validateSummary(item);
                break;
            case "progress":
                expression(item.get("value"), 0);
                if (item.has("max")) expression(item.get("max"), 0);
                break;
            case "deck":
                VisualSections.validateDeck(item);
                break;
            case "gantt":
                VisualPlan.validateGantt(item);
                break;
            case "diagram":
                VisualPlan.validateDiagram(item);
                break;
            case "map":
                VisualMap.validate(item);
                break;
            case "emoji": case "image": case "gallery":
                VisualMedia.validate(item);
                break;
            case "callout": case "quote": case "facts": case "tiles": case "ranking": case "status": case "rating":
                VisualShowcase.validate(item);
                break;
            case "palette": case "board": case "diff":
                VisualExplore.validate(item);
                break;
            case "calendar": case "tree": case "formula":
                VisualReference.validate(item);
                break;
            default:
                throw new IllegalArgumentException("Unknown component");
        }
    }

    void expression(Object expr, int depth) throws JSONException {
        require(depth <= 8);
        if (!(expr instanceof JSONObject)) {
            scalar(expr);
            return;
        }
        JSONObject node = (JSONObject) expr;
        if (node.has("var")) {
            require(node.length() == 1 && controls.containsKey(node.getString("var")));
            return;
        }
        String op = node.getString("op");
        int arity;
        switch (op) {
            case "not": case "count": case "sum": case "abs": case "round": arity = 1; break;
            case "if": arity = 3; break;
            case "+": case "-": case "*": case "/": case "pow": case "min": case "max":
            case "%": case "eq": case "lt": case "gt": case "and": case "or": case "contains":
            case "concat": arity = 2; break;
            default: throw new IllegalArgumentException("Unknown operator");
        }
        require(node.length() == 2);
        JSONArray args = array(node.get("args"), arity, arity);
        for (int i = 0; i < arity; i++) expression(args.get(i), depth + 1);
    }

    static Object evaluate(Object expr, Map<String, Object> state) {
        if (!(expr instanceof JSONObject)) return expr;
        JSONObject node = (JSONObject) expr;
        if (node.has("var")) return state.get(node.optString("var"));
        JSONArray args = node.optJSONArray("args");
        Object left = evaluate(args.opt(0), state);
        String op = node.optString("op");
        if ("if".equals(op)) {
            return left instanceof Boolean ? evaluate(args.opt((Boolean) left ? 1 : 2), state) : null;
        }
        if ("not".equals(op)) return left instanceof Boolean ? !(Boolean) left : null;
        if ("count".equals(op)) return left instanceof JSONArray ? ((JSONArray) left).length()
                : left instanceof String ? ((String) left).length() : null;
        if ("sum".equals(op)) return left instanceof JSONArray ? sum((JSONArray) left) : null;
        if ("abs".equals(op) || "round".equals(op)) {
            if (!(left instanceof Number)) return null;
            double value = ((Number) left).doubleValue();
            return "abs".equals(op) ? Math.abs(value) : Math.round(value);
        }
        Object right = evaluate(args.opt(1), state);
        if (left == null || right == null) return null;
        if ("eq".equals(op)) return same(left, right);
        if ("contains".equals(op)) {
            if (left instanceof JSONArray) return contains((JSONArray) left, right);
            return left instanceof String && right instanceof String ? ((String) left).contains((String) right) : null;
        }
        if ("concat".equals(op) && !(left instanceof JSONArray) && !(right instanceof JSONArray)) {
            String result = left.toString() + right;
            return result.length() <= 480 ? result : null;
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            if ("and".equals(op)) return (Boolean) left && (Boolean) right;
            if ("or".equals(op)) return (Boolean) left || (Boolean) right;
        }
        if (!(left instanceof Number) || !(right instanceof Number)) return null;
        double a = ((Number) left).doubleValue();
        double b = ((Number) right).doubleValue();
        double value;
        switch (op) {
            case "+": value = a + b; break;
            case "-": value = a - b; break;
            case "*": value = a * b; break;
            case "/": value = a / b; break;
            case "%": value = a % b; break;
            case "pow": value = Math.pow(a, b); break;
            case "min": value = Math.min(a, b); break;
            case "max": value = Math.max(a, b); break;
            case "lt": return a < b;
            case "gt": return a > b;
            default: return null;
        }
        return Double.isFinite(value) && Math.abs(value) <= 1e15 ? value : null;
    }

    private static Object sum(JSONArray values) {
        double total = 0;
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            if (!(value instanceof Number)) return null;
            total += ((Number) value).doubleValue();
        }
        return Math.abs(total) <= 1e15 ? total : null;
    }

    static boolean contains(JSONArray values, Object value) {
        for (int i = 0; i < values.length(); i++) if (same(values.opt(i), value)) return true;
        return false;
    }

    static boolean accepts(JSONObject control, Object value) throws JSONException {
        switch (control.getString("type")) {
            case "slider":
                double min = number(control.get("min"));
                double max = number(control.get("max"));
                double step = number(control.get("step"));
                if (!(value instanceof Number) || min >= max || step <= 0) return false;
                double steps = (max - min) / step;
                double tick = (number(value) - min) / step;
                return steps >= 1 && steps <= 1000.000001 && isWhole(steps)
                        && tick >= -0.000001 && tick <= steps + 0.000001 && isWhole(tick);
            case "toggle":
                return value instanceof Boolean;
            case "select":
                JSONArray options = array(control.get("options"), 1, 12);
                for (int i = 0; i < options.length(); i++) {
                    if (same(options.getJSONObject(i).get("value"), value)) return true;
                }
                return false;
            case "input":
                return VisualInputs.accepts(control, value);
            case "checklist":
                JSONArray choices = array(control.get("options"), 1, 12);
                JSONArray selected = array(value, 0, choices.length());
                JSONArray allowed = new JSONArray();
                for (int i = 0; i < choices.length(); i++) allowed.put(choices.getJSONObject(i).get("value"));
                for (int i = 0; i < selected.length(); i++) {
                    if (!contains(allowed, selected.get(i))) return false;
                    for (int j = 0; j < i; j++) if (same(selected.get(j), selected.get(i))) return false;
                }
                return true;
            default:
                return false;
        }
    }

    private static boolean isWhole(double value) { return Math.abs(value - Math.rint(value)) < 0.000001; }

    static boolean same(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return a == null ? b == null : a.equals(b);
    }

    static JSONArray array(Object value, int min, int max) {
        require(value instanceof JSONArray);
        JSONArray result = (JSONArray) value;
        require(result.length() >= min && result.length() <= max);
        return result;
    }

    static void scalar(Object value) {
        if (value instanceof String) string(value);
        else if (!(value instanceof Boolean)) number(value);
    }

    static double number(Object value) {
        require(value instanceof Number);
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number) && Math.abs(number) <= 1e15);
        return number;
    }

    static String string(Object value) {
        require(value instanceof String && ((String) value).length() <= 480);
        return (String) value;
    }

    static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid visualization");
    }

}
