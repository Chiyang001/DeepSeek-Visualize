package local.visualize;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualShowcase {
    private VisualShowcase() {}

    static View create(VisualCard card, JSONObject item) {
        switch (item.optString("type")) {
            case "callout": return callout(card, item);
            case "quote": return quote(card, item);
            case "facts": return facts(card, item);
            case "tiles": return tiles(card, item);
            case "ranking": return ranking(card, item);
            case "status": return status(card, item);
            case "rating": return rating(card, item);
            default: throw new AssertionError("Validated presentation component expected");
        }
    }

    private static View callout(VisualCard card, JSONObject item) {
        String tone = item.optString("tone", "info");
        int color = toneColor(card, tone);
        LinearLayout panel = card.section(tinted(card.surface, color), card.border);
        String symbol = item.optString("emoji");
        if (symbol.isEmpty()) {
            switch (tone) {
                case "success": symbol = "✓"; break;
                case "warning": symbol = "△"; break;
                case "danger": symbol = "!"; break;
                case "neutral": symbol = "•"; break;
                default: symbol = "ⓘ"; break;
            }
        }
        LinearLayout row = row(card);
        TextView marker = card.text(symbol, 17, color, true);
        marker.setGravity(Gravity.CENTER);
        marker.setMinWidth(card.dp(30));
        marker.setPadding(card.dp(4), card.dp(3), card.dp(4), card.dp(3));
        marker.setBackground(card.shape(tinted(card.panel, color), 8, 0));
        marker.setContentDescription(toneLabel(card, tone));
        row.addView(marker);
        LinearLayout content = column(card);
        title(card, content, item.optString("label"));
        addText(card, content, item.optString("text"), 14, card.ink, false,
                content.getChildCount() == 0 ? 0 : 4);
        addRemaining(card, row, content);
        panel.addView(row);
        return panel;
    }

    private static View quote(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        LinearLayout row = row(card);
        TextView marker = card.text("“", 34, card.accent, true);
        marker.setIncludeFontPadding(false);
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(marker);
        LinearLayout content = column(card);
        addText(card, content, item.optString("text"), 17, card.ink, false, 0);
        String attribution = item.optString("attribution");
        String source = item.optString("source");
        addText(card, content, attribution, 13, card.ink, true, 8);
        addText(card, content, source, 12, card.secondary, false, attribution.isEmpty() ? 8 : 2);
        addRemaining(card, row, content);
        card.append(panel, row, -2, panel.getChildCount() == 0 ? 0 : 8);
        return panel;
    }

    private static View facts(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        JSONArray entries = item.optJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (i > 0) divider(card, panel);
            addText(card, panel, entry.optString("label"), 12, card.secondary, true,
                    panel.getChildCount() == 0 ? 0 : 8);
            addText(card, panel, card.display(entry.opt("value"), 4, ""), 15, card.ink, true, 3);
            addText(card, panel, entry.optString("detail"), 13, card.secondary, false, 3);
        }
        return panel;
    }

    private static View tiles(VisualCard card, JSONObject item) {
        LinearLayout panel = column(card);
        title(card, panel, item.optString("label"));
        JSONArray entries = item.optJSONArray("entries");
        boolean grid = "grid".equals(item.optString("layout", "grid"));
        for (int i = 0; i < entries.length(); i++) {
            View tile = tile(card, entries.optJSONObject(i));
            if (grid && i + 1 < entries.length()) {
                tile = new TilePair(card, tile, tile(card, entries.optJSONObject(++i)));
            }
            card.append(panel, tile, -2, panel.getChildCount() == 0 ? 0 : 8);
        }
        return panel;
    }

    private static View tile(VisualCard card, JSONObject item) {
        int color = toneColor(card, item.optString("tone", "neutral"));
        LinearLayout panel = card.section(card.surface, card.border);
        addText(card, panel, item.optString("emoji"), 22, color, false, 0);
        addText(card, panel, item.optString("label"), 15, card.ink, true, panel.getChildCount() == 0 ? 0 : 4);
        addText(card, panel, item.optString("value"), 18, card.ink, true, 4);
        addText(card, panel, item.optString("detail"), 13, card.secondary, false, 5);
        String badge = item.optString("badge");
        if (!badge.isEmpty()) addBadge(card, panel, badge, color, 8);
        return panel;
    }

    private static View ranking(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        JSONArray entries = item.optJSONArray("entries");
        String unit = item.optString("unit");
        double max = 0;
        for (int i = 0; i < entries.length(); i++) max = Math.max(max, entries.optJSONObject(i).optDouble("value"));
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            LinearLayout row = row(card);
            TextView label = card.text((i + 1) + ".  " + entry.optString("label"), 14, card.ink, true);
            TextView value = card.text(card.display(entry.opt("value"), 4, unit), 13, card.ink, true);
            value.setMaxWidth(card.dp(145));
            value.setGravity(Gravity.END);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMarginEnd(card.dp(8));
            row.addView(label, params);
            row.addView(value);
            card.append(panel, row, -2, panel.getChildCount() == 0 ? 0 : 11);
            card.append(panel, bar(card, max == 0 ? 0 : entry.optDouble("value") / max, card.accent, 5), card.dp(5), 4);
            addText(card, panel, entry.optString("detail"), 12, card.secondary, false, 4);
        }
        return panel;
    }

    private static View status(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        JSONArray entries = item.optJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            if (i > 0) divider(card, panel);
            JSONObject entry = entries.optJSONObject(i);
            LinearLayout row = row(card);
            String state = entry.optString("state");
            int color = card.secondary;
            String symbol = "○";
            String status = card.local("待开始", "Pending");
            switch (state) {
                case "done":
                    color = card.success;
                    symbol = "✓";
                    status = card.local("已完成", "Done");
                    break;
                case "active":
                    color = card.accent;
                    symbol = "●";
                    status = card.local("进行中", "In progress");
                    break;
                case "blocked":
                    color = card.error;
                    symbol = "!";
                    status = card.local("受阻", "Blocked");
                    break;
                default:
                    break;
            }
            TextView marker = card.text(symbol, 14, color, true);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(card.shape(tinted(card.panel, color), 12, 0));
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(marker, new LinearLayout.LayoutParams(card.dp(25), card.dp(25)));
            LinearLayout content = column(card);
            addText(card, content, entry.optString("label"), 14, card.ink, true, 0);
            addText(card, content, status, 12, color, true, 2);
            addText(card, content, entry.optString("detail"), 13, card.secondary, false, 3);
            addRemaining(card, row, content);
            card.append(panel, row, -2, panel.getChildCount() == 0 ? 0 : 8);
        }
        return panel;
    }

    private static View rating(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.surface, card.border);
        title(card, panel, item.optString("label"));
        double value = item.optDouble("value");
        int max = item.optInt("max", 5);
        addText(card, panel, card.display(item.opt("value"), 4, "") + " / " + max, 25, card.ink, true, 2);
        LinearLayout scale = row(card);
        for (int i = 0; i < max; i++) {
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, card.dp(8), 1);
            if (i > 0) cell.setMarginStart(card.dp(3));
            scale.addView(bar(card, Math.max(0, Math.min(1, value - i)), card.accent, 8), cell);
        }
        card.append(panel, scale, -2, 7);
        addText(card, panel, item.optString("detail"), 13, card.secondary, false, 7);
        return panel;
    }

    private static void addBadge(VisualCard card, LinearLayout panel, String text, int color, int gap) {
        TextView badge = card.text(text, 12, color, true);
        badge.setPadding(card.dp(8), card.dp(2), card.dp(8), card.dp(2));
        badge.setBackground(card.shape(tinted(card.panel, color), 10, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = card.dp(gap);
        panel.addView(badge, params);
    }

    private static ProgressBar bar(VisualCard card, double fraction, int color, int height) {
        ProgressBar bar = new ProgressBar(card.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressDrawable(card.track(card.border, color, height));
        bar.setProgress((int) Math.round(fraction * 1000));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return bar;
    }

    private static LinearLayout row(VisualCard card) {
        LinearLayout row = new LinearLayout(card.getContext());
        row.setBaselineAligned(false);
        return row;
    }

    private static LinearLayout column(VisualCard card) {
        LinearLayout column = new LinearLayout(card.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static void title(VisualCard card, LinearLayout panel, String text) {
        addText(card, panel, text, 15, card.ink, true, 0);
    }

    private static void addText(VisualCard card, LinearLayout panel, String text, int size, int color,
                                boolean emphasis, int gap) {
        if (!text.isEmpty()) card.append(panel, card.text(text, size, color, emphasis), -2, gap);
    }

    private static void addRemaining(VisualCard card, LinearLayout row, LinearLayout content) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(card.dp(9));
        row.addView(content, params);
    }

    private static void divider(VisualCard card, LinearLayout panel) {
        View line = new View(card.getContext());
        line.setBackgroundColor(card.border);
        card.append(panel, line, card.dp(1), 8);
    }

    private static int tinted(int background, int accent) {
        return Color.rgb((Color.red(background) * 9 + Color.red(accent)) / 10,
                (Color.green(background) * 9 + Color.green(accent)) / 10,
                (Color.blue(background) * 9 + Color.blue(accent)) / 10);
    }

    private static int toneColor(VisualCard card, String tone) {
        switch (tone) {
            case "success": return card.success;
            case "warning": return Color.luminance(card.ink) > 0.5f ? 0xfff3c776 : 0xff805601;
            case "danger": return card.error;
            case "neutral": return card.secondary;
            default: return card.accent;
        }
    }

    private static String toneLabel(VisualCard card, String tone) {
        switch (tone) {
            case "success": return card.local("确认", "Success");
            case "warning": return card.local("注意", "Caution");
            case "danger": return card.local("警告", "Warning");
            case "neutral": return card.local("备注", "Note");
            default: return card.local("提示", "Information");
        }
    }

    private static final class TilePair extends LinearLayout {
        private final VisualCard card;

        TilePair(VisualCard card, View first, View second) {
            super(card.getContext());
            this.card = card;
            setBaselineAligned(false);
            addView(first, new LayoutParams(-1, -2));
            addView(second, new LayoutParams(-1, -2));
        }

        @Override protected void onMeasure(int width, int height) {
            boolean columns = MeasureSpec.getSize(width) >= card.dp(290)
                    && getResources().getConfiguration().fontScale <= 1.2f;
            setOrientation(columns ? HORIZONTAL : VERTICAL);
            for (int i = 0; i < 2; i++) {
                LayoutParams params = (LayoutParams) getChildAt(i).getLayoutParams();
                params.width = columns ? 0 : LayoutParams.MATCH_PARENT;
                params.height = columns ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
                params.weight = columns ? 1 : 0;
                params.setMarginStart(columns && i == 1 ? card.dp(8) : 0);
                params.topMargin = !columns && i == 1 ? card.dp(8) : 0;
            }
            super.onMeasure(width, height);
        }
    }

    static void validate(JSONObject item) throws JSONException {
        String type = item.getString("type");
        switch (type) {
            case "callout":
                requiredText(item, "text", 480);
                optionalText(item, "emoji", 32);
                validateTone(item);
                return;
            case "quote":
                requiredText(item, "text", 480);
                optionalText(item, "attribution", 100);
                optionalText(item, "source", 160);
                return;
            case "rating":
                double value = VisualSpec.number(item.get("value"));
                double max = item.has("max") ? VisualSpec.number(item.get("max")) : 5;
                VisualSpec.require(max >= 2 && max <= 10 && max == Math.rint(max) && value >= 0 && value <= max);
                optionalText(item, "detail", 300);
                return;
            default:
                break;
        }
        if ("tiles".equals(type)) {
            String layout = item.has("layout") ? VisualSpec.string(item.get("layout")) : "grid";
            VisualSpec.require("grid".equals(layout) || "list".equals(layout));
        }
        JSONArray entries = VisualSpec.array(item.get("entries"), 1, "tiles".equals(type) ? 8 : 12);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            requiredText(entry, "label", 100);
            optionalText(entry, "detail", 300);
            switch (type) {
                case "facts":
                    VisualSpec.scalar(entry.get("value"));
                    break;
                case "tiles":
                    optionalText(entry, "emoji", 32);
                    optionalText(entry, "value", 120);
                    optionalText(entry, "badge", 48);
                    validateTone(entry);
                    break;
                case "ranking":
                    VisualSpec.require(VisualSpec.number(entry.get("value")) >= 0);
                    break;
                case "status":
                    String state = VisualSpec.string(entry.get("state"));
                    VisualSpec.require("done".equals(state) || "active".equals(state)
                            || "pending".equals(state) || "blocked".equals(state));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown presentation component");
            }
        }
    }

    private static void validateTone(JSONObject item) throws JSONException {
        if (!item.has("tone")) return;
        String tone = VisualSpec.string(item.get("tone"));
        VisualSpec.require("info".equals(tone) || "success".equals(tone) || "warning".equals(tone)
                || "danger".equals(tone) || "neutral".equals(tone));
    }

    private static void requiredText(JSONObject item, String field, int limit) throws JSONException {
        String text = VisualSpec.string(item.get(field));
        VisualSpec.require(!text.trim().isEmpty() && text.length() <= limit);
    }

    private static void optionalText(JSONObject item, String field, int limit) throws JSONException {
        if (item.has(field)) VisualSpec.require(VisualSpec.string(item.get(field)).length() <= limit);
    }
}
