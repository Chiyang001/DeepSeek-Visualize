package local.visualize;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualReference {
    private VisualReference() {}

    static View create(VisualCard card, JSONObject item) {
        LinearLayout frame = card.section(card.surface, card.border);
        String label = item.optString("label");
        if (!label.isEmpty()) frame.addView(card.text(label, 16, card.ink, true));
        switch (item.optString("type")) {
            case "calendar":
                calendar(card, frame, item);
                break;
            case "tree":
                tree(card, frame, item.optJSONArray("nodes"));
                break;
            case "formula":
                formula(card, frame, item);
                break;
            default:
                throw new AssertionError("Unknown reference component");
        }
        return frame;
    }

    private static void calendar(VisualCard card, LinearLayout frame, JSONObject item) {
        String month = item.optString("month");
        GregorianCalendar date = monthStart(month);
        JSONArray events = item.optJSONArray("entries");
        int[] counts = new int[32];
        for (int i = 0; i < events.length(); i++) counts[events.optJSONObject(i).optInt("day")]++;

        card.append(frame, card.text(month, 15, card.secondary, true), -2, 5);
        LinearLayout weekday = new LinearLayout(card.getContext());
        String[] names = card.local("一 二 三 四 五 六 日", "Mon Tue Wed Thu Fri Sat Sun").split(" ");
        for (String name : names) {
            TextView text = card.text(name, 11, card.secondary, true);
            text.setGravity(Gravity.CENTER);
            weekday.addView(text, new LinearLayout.LayoutParams(0, card.dp(24), 1));
        }
        card.append(frame, weekday, -2, 7);
        int leading = (date.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int week = 0; week * 7 < leading + days; week++) {
            LinearLayout row = new LinearLayout(card.getContext());
            for (int column = 0; column < 7; column++) {
                int day = week * 7 + column - leading + 1;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, card.dp(34), 1);
                params.setMarginStart(card.dp(2));
                params.setMarginEnd(card.dp(2));
                if (day < 1 || day > days) {
                    row.addView(new View(card.getContext()), params);
                    continue;
                }
                TextView cell = card.text(Integer.toString(day), 13, counts[day] > 0 ? card.accent : card.ink,
                        counts[day] > 0);
                cell.setGravity(Gravity.CENTER);
                if (counts[day] > 0) {
                    cell.setBackground(card.shape(card.panel, 9, card.accent));
                    cell.setContentDescription(card.local(day + " 日，" + counts[day] + " 项安排",
                            day + ", events " + counts[day]));
                }
                row.addView(cell, params);
            }
            card.append(frame, row, -2, 4);
        }
        for (int day = 1; day <= days; day++) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event.optInt("day") != day) continue;
                String tag = card.local(day + " 日", Integer.toString(day));
                card.append(frame, referenceRow(card, tag, event.optString("label"),
                        event.optString("detail")), -2, 9);
            }
        }
    }

    private static void tree(VisualCard card, LinearLayout frame, JSONArray nodes) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            int depth = node.optInt("depth");
            LinearLayout row = referenceRow(card, depth == 0 ? "●" : "└", node.optString("label"),
                    node.optString("detail"));
            row.setPaddingRelative(card.dp(depth * 16), 0, 0, 0);
            card.append(frame, row, -2, 8);
        }
    }

    private static void formula(VisualCard card, LinearLayout frame, JSONObject item) {
        TextView expression = card.text(item.optString("expression"), 21, card.ink, true);
        expression.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        expression.setPadding(card.dp(12), card.dp(9), card.dp(12), card.dp(9));
        expression.setBackground(card.shape(card.panel, 10, 0));
        card.append(frame, expression, -2, 8);
        JSONArray variables = item.optJSONArray("variables");
        for (int i = 0; variables != null && i < variables.length(); i++) {
            JSONObject variable = variables.optJSONObject(i);
            card.append(frame, referenceRow(card, variable.optString("symbol"), variable.optString("meaning"), ""),
                    -2, 7);
        }
        String result = item.optString("result");
        if (!result.isEmpty()) card.append(frame, card.text(result, 15, card.accent, true), -2, 9);
    }

    private static LinearLayout referenceRow(VisualCard card, String tag, String label, String detail) {
        LinearLayout row = new LinearLayout(card.getContext());
        row.setBaselineAligned(false);
        TextView marker = card.text(tag, 12, card.accent, true);
        marker.setMinWidth(card.dp(22));
        marker.setMaxWidth(card.dp(100));
        marker.setPaddingRelative(0, card.dp(2), card.dp(8), 0);
        row.addView(marker);
        LinearLayout content = new LinearLayout(card.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(card.text(label, 14, card.ink, true));
        if (!detail.isEmpty()) card.append(content, card.text(detail, 13, card.secondary, false), -2, 3);
        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    static void validate(JSONObject item) throws JSONException {
        switch (item.getString("type")) {
            case "calendar":
                String month = VisualSpec.string(item.get("month"));
                VisualSpec.require(month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"));
                int days = monthStart(month).getActualMaximum(Calendar.DAY_OF_MONTH);
                JSONArray events = VisualSpec.array(item.get("entries"), 0, 16);
                for (int i = 0; i < events.length(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    integer(event.get("day"), 1, days);
                    requiredText(event, "label", 80);
                    optionalText(event, "detail");
                }
                break;
            case "tree":
                JSONArray nodes = VisualSpec.array(item.get("nodes"), 1, 18);
                int previous = -1;
                for (int i = 0; i < nodes.length(); i++) {
                    JSONObject node = nodes.getJSONObject(i);
                    previous = integer(node.get("depth"), 0, Math.min(3, previous + 1));
                    requiredText(node, "label", 80);
                    optionalText(node, "detail");
                }
                break;
            case "formula":
                requiredText(item, "expression", 220);
                optionalText(item, "result");
                if (item.has("variables")) {
                    JSONArray variables = VisualSpec.array(item.get("variables"), 0, 10);
                    for (int i = 0; i < variables.length(); i++) {
                        JSONObject variable = variables.getJSONObject(i);
                        requiredText(variable, "symbol", 16);
                        requiredText(variable, "meaning", 120);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown reference component");
        }
    }

    private static GregorianCalendar monthStart(String month) {
        GregorianCalendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        date.setGregorianChange(new java.util.Date(Long.MIN_VALUE));
        date.clear();
        date.set(Integer.parseInt(month.substring(0, 4)), Integer.parseInt(month.substring(5)) - 1, 1);
        return date;
    }

    private static int integer(Object value, int min, int max) {
        double number = VisualSpec.number(value);
        VisualSpec.require(number >= min && number <= max && number == Math.rint(number));
        return (int) number;
    }

    private static void requiredText(JSONObject item, String key, int max) throws JSONException {
        String value = VisualSpec.string(item.get(key));
        VisualSpec.require(!value.trim().isEmpty() && value.length() <= max);
    }

    private static void optionalText(JSONObject item, String key) throws JSONException {
        if (item.has(key)) VisualSpec.string(item.get(key));
    }
}
