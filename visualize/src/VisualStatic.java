package local.visualize;

import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualStatic {
    private VisualStatic() {}

    static View summary(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        JSONArray metrics = item.optJSONArray("metrics");
        boolean hero = "hero".equals(item.optString("layout", "hero"));
        if (hero) {
            card.append(panel, stat(card, metrics.optJSONObject(0), 34), -2, 4);
            if (metrics.length() > 1) divider(card, panel, 12);
            for (int i = 1; i < metrics.length(); i += 2) {
                LinearLayout row = new LinearLayout(card.getContext());
                row.setBaselineAligned(false);
                for (int j = i; j < Math.min(i + 2, metrics.length()); j++) {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                    if (j == i && j + 1 < metrics.length()) params.setMarginEnd(card.dp(12));
                    row.addView(stat(card, metrics.optJSONObject(j), 23), params);
                }
                card.append(panel, row, -2, 10);
            }
        } else {
            for (int i = 0; i < metrics.length(); i++) {
                if (i != 0) divider(card, panel, 12);
                card.append(panel, stat(card, metrics.optJSONObject(i), 27), -2, i == 0 ? 4 : 11);
            }
        }
        String note = item.optString("note");
        if (!note.isEmpty()) card.append(panel, card.text(note, 13, card.secondary, false), -2, 12);
        return panel;
    }

    private static View stat(VisualCard card, JSONObject metric, int valueSize) {
        LinearLayout column = column(card);
        column.addView(card.text(metric.optString("label"), 13, card.secondary, false));
        String value = card.display(metric.opt("value"), metric.optInt("digits", 2), metric.optString("unit"));
        TextView amount = card.text(value, valueSize, card.ink, true);
        String badge = metric.optString("badge");
        if (badge.isEmpty()) {
            card.append(column, amount, -2, 3);
        } else {
            LinearLayout line = new LinearLayout(card.getContext());
            line.setBaselineAligned(false);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(amount, new LinearLayout.LayoutParams(0, -2, 1));
            TextView chip = card.text(badge, 12, tone(card, metric.optString("tone")), true);
            chip.setPadding(card.dp(9), card.dp(3), card.dp(9), card.dp(3));
            chip.setMaxWidth(card.dp(valueSize < 25 ? 74 : 126));
            chip.setBackground(card.shape(card.panel, 20, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginStart(card.dp(7));
            line.addView(chip, params);
            card.append(column, line, -2, 3);
        }
        String detail = metric.optString("detail");
        if (!detail.isEmpty()) card.append(column, card.text(detail, 13, card.secondary, false), -2, 5);
        return column;
    }

    private static int tone(VisualCard card, String tone) {
        switch (tone) {
            case "positive": return card.success;
            case "negative": return card.error;
            case "accent": return card.accent;
            default: return card.secondary;
        }
    }

    static View table(VisualCard card, JSONObject item) {
        LinearLayout frame = column(card);
        title(card, frame, item.optString("label"));
        JSONArray columns = item.optJSONArray("columns"), rows = item.optJSONArray("rows");
        boolean[] numeric = new boolean[columns.length()], emphasis = new boolean[columns.length()];
        JSONArray highlighted = item.optJSONArray("emphasis");
        if (highlighted != null) for (int i = 0; i < highlighted.length(); i++) emphasis[highlighted.optInt(i)] = true;
        for (int c = 0; c < columns.length(); c++) {
            boolean seen = false, numbers = true;
            for (int r = 0; r < rows.length(); r++) {
                Object value = rows.optJSONArray(r).opt(c);
                if (value == null || value == JSONObject.NULL || "".equals(value)) continue;
                seen = true;
                numbers &= value instanceof Number || value instanceof String
                        && ((String) value).matches("[+−-]?[0-9,.]+%?");
            }
            numeric[c] = seen && numbers;
        }
        LinearLayout grid = column(card);
        tableRow(card, grid, columns, numeric, emphasis, true);
        for (int i = 0; i < rows.length(); i++) {
            divider(card, grid, 0);
            tableRow(card, grid, rows.optJSONArray(i), numeric, emphasis, false);
        }
        if (columns.length() > 4) {
            HorizontalScrollView viewport = new HorizontalScrollView(card.getContext());
            viewport.addView(grid, new HorizontalScrollView.LayoutParams(card.dp(88 * columns.length()), -2));
            card.append(frame, viewport, -2, 5);
        } else card.append(frame, grid, -2, 5);
        return frame;
    }

    private static void tableRow(VisualCard card, LinearLayout grid, JSONArray values, boolean[] numeric,
                                 boolean[] emphasis, boolean heading) {
        LinearLayout row = new LinearLayout(card.getContext());
        row.setBaselineAligned(false);
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            String label = value == null || value == JSONObject.NULL ? "—" : card.display(value, 4, "");
            TextView cell = card.text(label, heading ? 12 : 13, !heading && emphasis[i] ? card.accent : card.ink,
                    heading || emphasis[i]);
            cell.setGravity((numeric[i] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            cell.setPadding(card.dp(5), card.dp(8), card.dp(5), card.dp(8));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, i == 0 ? 1.22f : 1f));
        }
        card.append(grid, row, -2, 0);
    }

    static View sequence(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        JSONArray entries = item.optJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            if (i != 0) divider(card, panel, 10);
            JSONObject entry = entries.optJSONObject(i);
            String tag = entry.optString("tag");
            LinearLayout row = new LinearLayout(card.getContext());
            row.setBaselineAligned(false);
            TextView stamp = card.text(tag.isEmpty() ? String.format(java.util.Locale.ROOT, "%02d", i + 1) : tag,
                    12, card.accent, true);
            stamp.setMaxWidth(card.dp(86));
            stamp.setPadding(card.dp(7), card.dp(3), card.dp(7), card.dp(3));
            stamp.setBackground(card.shape(card.panel, 12, 0));
            LinearLayout.LayoutParams marker = new LinearLayout.LayoutParams(-2, -2);
            marker.setMarginEnd(card.dp(9));
            row.addView(stamp, marker);
            LinearLayout content = column(card);
            content.addView(card.text(entry.optString("label"), 14, card.ink, true));
            card.append(content, card.text(entry.optString("detail"), 13, card.secondary, false), -2, 4);
            row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
            card.append(panel, row, -2, 10);
        }
        return panel;
    }

    private static LinearLayout column(VisualCard card) {
        LinearLayout column = new LinearLayout(card.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static void title(VisualCard card, LinearLayout panel, String label) {
        if (!label.isEmpty()) panel.addView(card.text(label, 15, card.ink, true));
    }

    private static void divider(VisualCard card, LinearLayout panel, int margin) {
        View line = new View(card.getContext());
        line.setBackgroundColor(card.border);
        card.append(panel, line, card.dp(1), margin);
    }

    static void validateSummary(JSONObject item) throws JSONException {
        String layout = item.optString("layout", "hero");
        VisualSpec.require("hero".equals(layout) || "rows".equals(layout));
        if (item.has("note")) VisualSpec.string(item.get("note"));
        JSONArray metrics = VisualSpec.array(item.get("metrics"), 1, 6);
        for (int i = 0; i < metrics.length(); i++) {
            JSONObject metric = metrics.getJSONObject(i);
            VisualSpec.string(metric.get("label"));
            Object value = metric.get("value");
            VisualSpec.require(value instanceof String || value instanceof Number);
            VisualSpec.scalar(value);
            for (String field : new String[]{"unit", "badge", "detail"}) {
                if (metric.has(field)) VisualSpec.string(metric.get(field));
            }
            if (metric.has("tone")) {
                String tone = VisualSpec.string(metric.get("tone"));
                VisualSpec.require("positive".equals(tone) || "negative".equals(tone)
                        || "accent".equals(tone) || "neutral".equals(tone));
            }
            if (metric.has("digits")) {
                double digits = VisualSpec.number(metric.get("digits"));
                VisualSpec.require(digits >= 0 && digits <= 4 && digits == Math.rint(digits));
            }
        }
    }
}
