package local.visualize;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualExplore {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final int MAX_DIFF_LINES = 24;

    private VisualExplore() {}

    static View create(VisualCard card, JSONObject item) {
        switch (item.optString("type")) {
            case "palette": return palette(card, item);
            case "board": return new Board(card, item);
            case "diff": return diff(card, item);
            default: throw new AssertionError("Validated exploration component expected");
        }
    }

    private static View palette(VisualCard card, JSONObject item) {
        LinearLayout frame = section(card, item);
        JSONArray swatches = item.optJSONArray("swatches");
        if (!card.isReadOnly()) {
            card.append(frame, card.text(card.local("轻触色块复制色值", "Tap a swatch to copy its hex value"),
                    12, card.secondary, false), -2, 4);
        }
        for (int i = 0; i < swatches.length(); i++) {
            JSONObject swatch = swatches.optJSONObject(i);
            String hex = swatch.optString("hex").toUpperCase(Locale.ROOT);
            LinearLayout row = new LinearLayout(card.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(card.dp(48));
            row.setPadding(card.dp(6), card.dp(4), card.dp(6), card.dp(4));

            View chip = new View(card.getContext());
            chip.setBackground(card.shape(0xff000000 | Integer.parseInt(hex.substring(1), 16), 10, card.border));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(card.dp(52), card.dp(42));
            chipParams.setMarginEnd(card.dp(12));
            row.addView(chip, chipParams);

            LinearLayout text = new LinearLayout(card.getContext());
            text.setOrientation(LinearLayout.VERTICAL);
            String name = swatch.optString("label");
            if (!name.isEmpty()) text.addView(card.text(name, 14, card.ink, true));
            TextView code = card.text(hex, 13, name.isEmpty() ? card.ink : card.secondary, false);
            code.setTypeface(Typeface.MONOSPACE);
            text.addView(code);
            addDetail(card, text, swatch.optString("detail"));
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

            if (!card.isReadOnly()) {
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(card.border),
                        card.shape(card.panel, 12, 0), null));
                row.setFocusable(true);
                row.setOnClickListener(view -> copyColor(card, hex));
            }
            card.append(frame, row, -2, 7);
        }
        return frame;
    }

    private static void copyColor(VisualCard card, String hex) {
        ClipboardManager clipboard = card.getContext().getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(hex, hex));
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(card.getContext(), card.local("已复制 ", "Copied ") + hex, Toast.LENGTH_SHORT).show();
        }
    }

    private static final class Board extends LinearLayout {
        private final VisualCard card;
        private final JSONObject item;
        private final String[] lanes;
        private final List<BoardEntry> entries = new ArrayList<>();
        private final LinearLayout columns;

        Board(VisualCard card, JSONObject item) {
            super(card.getContext());
            this.card = card;
            this.item = item;
            setOrientation(VERTICAL);
            String title = item.optString("label");
            if (!title.isEmpty()) addView(card.text(title, 15, card.ink, true));
            columns = new LinearLayout(card.getContext());
            columns.setOrientation(VERTICAL);
            addView(columns);
            JSONArray source = item.optJSONArray("columns");
            lanes = new String[source.length()];
            for (int i = 0; i < source.length(); i++) {
                JSONObject lane = source.optJSONObject(i);
                lanes[i] = lane.optString("label");
                JSONArray cards = lane.optJSONArray("cards");
                for (int j = 0; j < cards.length(); j++) entries.add(new BoardEntry(cards.optJSONObject(j), i));
            }
            render();
        }

        private void render() {
            columns.removeAllViews();
            for (int i = 0; i < lanes.length; i++) {
                LinearLayout lane = card.section(card.panel, card.border);
                int count = 0;
                for (BoardEntry entry : entries) if (entry.lane == i) count++;
                TextView counter = card.text(String.valueOf(count), 12, card.secondary, true);
                counter.setGravity(Gravity.END);
                lane.addView(card.split(card.text(lanes[i], 14, card.ink, true), counter));
                if (count == 0) addDetail(card, lane, card.local("暂无卡片", "No cards"));
                for (BoardEntry entry : entries) {
                    if (entry.lane == i) card.append(lane, tile(entry), -2, 8);
                }
                card.append(columns, lane, -2, 8);
            }
        }

        private View tile(BoardEntry entry) {
            LinearLayout tile = card.section(card.surface, 0);
            String tag = entry.data.optString("tag");
            if (!tag.isEmpty()) tile.addView(card.text(tag, 12, card.accent, true));
            String label = entry.data.optString("label");
            card.append(tile, card.text(label, 14, card.ink, true), -2, tag.isEmpty() ? 0 : 3);
            addDetail(card, tile, entry.data.optString("detail"));
            if (!card.isReadOnly()) {
                Button move = card.button(card.local("移动到…", "Move to…"), false);
                move.setContentDescription(card.local("移动卡片 ", "Move card ") + label);
                move.setOnClickListener(view -> move(entry));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.topMargin = card.dp(6);
                tile.addView(move, params);
            }
            return tile;
        }

        private void move(BoardEntry entry) {
            int from = entry.lane;
            String[] alternatives = new String[lanes.length - 1];
            for (int i = 0; i < alternatives.length; i++) alternatives[i] = lanes[i < from ? i : i + 1];
            card.dialog().setTitle(entry.data.optString("label")).setItems(alternatives, (dialog, choice) -> {
                entry.lane = choice < from ? choice : choice + 1;
                render();
                announceForAccessibility(card.local("已移动到 ", "Moved to ") + lanes[entry.lane]);
                if (!card.hasContext()) return;
                JSONArray state = new JSONArray();
                for (int i = 0; i < entries.size(); i++) {
                    BoardEntry current = entries.get(i);
                    state.put(VisualContext.json("card_number", i + 1, "card", VisualCard.shortLabel(current.data),
                            "column_number", current.lane + 1,
                            "column", VisualContext.shortText(lanes[current.lane], 72)));
                }
                card.recordContext(item, "board", "board_arrangement", VisualContext.json(
                        "board", VisualCard.shortLabel(item), "cards", state));
            }).setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private static final class BoardEntry {
        final JSONObject data;
        int lane;

        BoardEntry(JSONObject data, int lane) {
            this.data = data;
            this.lane = lane;
        }
    }

    private static View diff(VisualCard card, JSONObject item) {
        LinearLayout frame = section(card, item);
        String beforeLabel = item.optString("beforeLabel", card.local("原版", "Before"));
        String afterLabel = item.optString("afterLabel", card.local("新版", "After"));
        String[] before = lines(item.optString("before"));
        String[] after = lines(item.optString("after"));
        List<DiffLine> lines = compare(before, after);
        int additions = 0, removals = 0;
        for (DiffLine line : lines) {
            if (line.marker == '+') additions++;
            if (line.marker == '−') removals++;
        }
        if (additions + removals == 0) {
            addDetail(card, frame, card.local("两版内容相同", "Both versions are identical"));
        } else {
            TextView removed = card.text("−" + removals + "  " + beforeLabel, 13, card.error, true);
            TextView added = card.text("+" + additions + "  " + afterLabel, 13, card.success, true);
            card.append(frame, card.split(removed, added), -2, 6);
        }
        List<View> contextRows = new ArrayList<>();
        for (DiffLine line : lines) {
            TextView row = diffRow(card, line);
            card.append(frame, row, -2, 4);
            if (line.marker == ' ') contextRows.add(row);
        }
        if (!card.isReadOnly() && additions + removals > 0 && !contextRows.isEmpty()) {
            Button filter = card.button(card.local("只看改动", "Show changes only"), false);
            filter.setOnClickListener(view -> {
                boolean filtered = contextRows.get(0).getVisibility() == View.VISIBLE;
                for (View row : contextRows) row.setVisibility(filtered ? View.GONE : View.VISIBLE);
                filter.setText(filtered ? card.local("显示全文", "Show all lines")
                        : card.local("只看改动", "Show changes only"));
            });
            card.append(frame, filter, -2, 8);
        }
        return frame;
    }

    private static TextView diffRow(VisualCard card, DiffLine line) {
        int color = line.marker == '+' ? card.success : line.marker == '−' ? card.error : card.ink;
        String text = visibleWhitespace(line.text, line.marker != ' ');
        TextView row = card.text(line.marker + "  " + text, 13, color, false);
        row.setTypeface(Typeface.MONOSPACE);
        row.setPadding(card.dp(8), card.dp(5), card.dp(8), card.dp(5));
        row.setBackground(card.shape(card.panel, 7, 0));
        return row;
    }

    private static String visibleWhitespace(String text, boolean changed) {
        if (text.isEmpty()) return "⏎";
        if (!changed) return text.replace("\t", "⇥");
        int first = 0, last = text.length();
        while (first < last && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) first++;
        while (last > first && (text.charAt(last - 1) == ' ' || text.charAt(last - 1) == '\t')) last--;
        return markIndent(text.substring(0, first)) + text.substring(first, last).replace("\t", "⇥")
                + markIndent(text.substring(last));
    }

    private static String markIndent(String spaces) { return spaces.replace(' ', '·').replace('\t', '⇥'); }

    private static String[] lines(String text) { return text.isEmpty() ? new String[0] : text.split("\\r\\n|\\n|\\r", -1); }

    private static List<DiffLine> compare(String[] before, String[] after) {
        int[][] common = new int[before.length + 1][after.length + 1];
        for (int i = before.length - 1; i >= 0; i--) {
            for (int j = after.length - 1; j >= 0; j--) {
                common[i][j] = before[i].equals(after[j]) ? 1 + common[i + 1][j + 1]
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<DiffLine> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < before.length || j < after.length) {
            if (i < before.length && j < after.length && before[i].equals(after[j])) {
                result.add(new DiffLine(' ', before[i++]));
                j++;
            } else if (i < before.length && (j == after.length || common[i + 1][j] >= common[i][j + 1])) {
                result.add(new DiffLine('−', before[i++]));
            } else {
                result.add(new DiffLine('+', after[j++]));
            }
        }
        return result;
    }

    private static final class DiffLine {
        final char marker;
        final String text;

        DiffLine(char marker, String text) {
            this.marker = marker;
            this.text = text;
        }
    }

    private static LinearLayout section(VisualCard card, JSONObject item) {
        LinearLayout frame = card.section(card.surface, card.border);
        String title = item.optString("label");
        if (!title.isEmpty()) frame.addView(card.text(title, 15, card.ink, true));
        return frame;
    }

    private static void addDetail(VisualCard card, LinearLayout parent, String detail) {
        if (!detail.isEmpty()) card.append(parent, card.text(detail, 13, card.secondary, false), -2, 3);
    }

    static void validate(JSONObject item) throws JSONException {
        switch (item.getString("type")) {
            case "palette":
                JSONArray swatches = VisualSpec.array(item.get("swatches"), 1, 10);
                for (int i = 0; i < swatches.length(); i++) {
                    JSONObject swatch = swatches.getJSONObject(i);
                    VisualSpec.require(HEX.matcher(VisualSpec.string(swatch.get("hex"))).matches());
                    optionalStrings(swatch, "label", "detail");
                }
                break;
            case "board":
                JSONArray columns = VisualSpec.array(item.get("columns"), 2, 4);
                Set<String> labels = new HashSet<>();
                int total = 0;
                for (int i = 0; i < columns.length(); i++) {
                    JSONObject column = columns.getJSONObject(i);
                    String label = VisualSpec.string(column.get("label")).trim();
                    VisualSpec.require(!label.isEmpty() && labels.add(label));
                    JSONArray cards = VisualSpec.array(column.get("cards"), 0, 16);
                    total += cards.length();
                    VisualSpec.require(total <= 16);
                    for (int j = 0; j < cards.length(); j++) {
                        JSONObject entry = cards.getJSONObject(j);
                        VisualSpec.require(!VisualSpec.string(entry.get("label")).trim().isEmpty());
                        optionalStrings(entry, "detail", "tag");
                    }
                }
                VisualSpec.require(total > 0);
                break;
            case "diff":
                String before = VisualSpec.string(item.get("before"));
                String after = VisualSpec.string(item.get("after"));
                VisualSpec.require(lines(before).length <= MAX_DIFF_LINES && lines(after).length <= MAX_DIFF_LINES);
                optionalStrings(item, "beforeLabel", "afterLabel");
                break;
            default:
                throw new IllegalArgumentException("Unknown exploration component");
        }
    }

    private static void optionalStrings(JSONObject item, String... fields) throws JSONException {
        for (String field : fields) {
            if (item.has(field)) VisualSpec.string(item.get(field));
        }
    }
}
