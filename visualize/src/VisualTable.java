package local.visualize;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualTable extends LinearLayout {
    private static final int PAGE_SIZE = 8;
    private final VisualCard card;
    private final JSONObject component;
    private final String[] columns;
    private final Object[][] rows;
    private final int[] widths;
    private final boolean[] editable;
    private final LinearLayout grid, pages;
    private final TextView pageLabel;
    private final Button previous, next;
    private final ArrayList<Object[]> visible = new ArrayList<>();
    private String filter = "";
    private int page, sortColumn = -1;
    private boolean descending;

    VisualTable(VisualCard card, JSONObject item) {
        super(card.getContext());
        this.card = card;
        component = item;
        setOrientation(VERTICAL);
        JSONArray keys = item.optJSONArray("columns"), data = item.optJSONArray("rows");
        columns = new String[keys.length()];
        widths = new int[keys.length()];
        editable = new boolean[keys.length()];
        rows = new Object[data.length()][keys.length()];
        for (int i = 0; i < keys.length(); i++) columns[i] = keys.optString(i);
        JSONArray editing = item.optJSONArray("editable");
        if (editing != null) for (int i = 0; i < editing.length(); i++) editable[editing.optInt(i)] = true;
        for (int i = 0; i < data.length(); i++) {
            for (int j = 0; j < keys.length(); j++) rows[i][j] = data.optJSONArray(i).opt(j);
        }
        for (int i = 0; i < columns.length; i++) {
            int length = columns[i].length();
            for (Object[] row : rows) length = Math.max(length, cellText(row[i]).length());
            widths[i] = card.dp(Math.max(editable[i] ? 105 : 76, Math.min(186, length * 9 + 32)));
        }

        TextView title = card.text(item.optString("label"), 15, card.ink, true);
        addView(title);
        VisualField search = new VisualField(card, card.local("搜索表格", "Search table"), InputType.TYPE_CLASS_TEXT);
        card.append(this, search, LayoutParams.WRAP_CONTENT, 8);

        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setBackground(card.shape(card.panel, 12, card.border));
        scroll.setClipToOutline(true);
        grid = new LinearLayout(getContext());
        grid.setOrientation(VERTICAL);
        scroll.addView(grid, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        card.append(this, scroll, LayoutParams.WRAP_CONTENT, 8);

        pages = new LinearLayout(getContext());
        pages.setGravity(Gravity.CENTER_VERTICAL);
        previous = card.button("‹", false);
        previous.setContentDescription(card.local("上一页", "Previous page"));
        next = card.button("›", false);
        next.setContentDescription(card.local("下一页", "Next page"));
        pageLabel = card.text("", 13, card.secondary, false);
        pageLabel.setGravity(Gravity.CENTER);
        pages.addView(previous, new LayoutParams(card.dp(49), LayoutParams.WRAP_CONTENT));
        pages.addView(pageLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        pages.addView(next, new LayoutParams(card.dp(49), LayoutParams.WRAP_CONTENT));
        card.append(this, pages, LayoutParams.WRAP_CONTENT, 8);
        Button copy = card.button(card.local("复制当前表格", "Copy table"), false);
        card.append(this, copy, LayoutParams.WRAP_CONTENT, 8);
        copy.setOnClickListener(view -> copy());
        previous.setOnClickListener(view -> { page--; render(); });
        next.setOnClickListener(view -> { page++; render(); });
        search.onText(text -> {
            filter = text.trim().toLowerCase(Locale.ROOT);
            page = 0;
            render();
        });
        render();
    }

    private void render() {
        visible.clear();
        for (Object[] row : rows) {
            for (Object cell : row) {
                if (cellText(cell).toLowerCase(Locale.ROOT).contains(filter)) {
                    visible.add(row);
                    break;
                }
            }
        }
        if (sortColumn >= 0) {
            int column = sortColumn;
            visible.sort((a, b) -> descending ? compare(b[column], a[column]) : compare(a[column], b[column]));
        }
        int last = Math.max(0, (visible.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, last));
        grid.removeAllViews();
        LinearLayout header = row();
        for (int i = 0; i < columns.length; i++) {
            final int column = i;
            String arrow = sortColumn == i ? (descending ? " ↓" : " ↑") : " ↕";
            TextView cell = cell(header, columns[i] + arrow, i, true);
            cell.setOnClickListener(view -> {
                descending = sortColumn == column && !descending;
                sortColumn = column;
                page = 0;
                render();
            });
            ripple(cell);
        }
        if (visible.isEmpty()) {
            TextView empty = card.text(card.local("没有匹配的行", "No matching rows"), 14, card.secondary, false);
            empty.setPadding(card.dp(10), card.dp(10), card.dp(10), card.dp(10));
            grid.addView(empty);
        }
        for (int i = page * PAGE_SIZE; i < Math.min(visible.size(), (page + 1) * PAGE_SIZE); i++) {
            LinearLayout rowView = row();
            Object[] record = visible.get(i);
            for (int j = 0; j < columns.length; j++) {
                final int column = j;
                String value = cellText(record[j]);
                String display = editable[j] && value.isEmpty() ? card.local("点此填写", "Tap to fill") : value;
                TextView cell = cell(rowView, display, j, false);
                if (editable[j]) {
                    cell.setTextColor(card.accent);
                    cell.setContentDescription(columns[j] + " · " + display);
                    cell.setOnClickListener(view -> edit(record, column));
                    ripple(cell);
                }
            }
        }
        pages.setVisibility(last > 0 ? VISIBLE : GONE);
        previous.setEnabled(page > 0);
        next.setEnabled(page < last);
        pageLabel.setText((page + 1) + " / " + (last + 1));
    }

    private LinearLayout row() {
        if (grid.getChildCount() > 0) {
            View divider = new View(getContext());
            divider.setBackgroundColor(card.border);
            grid.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, card.dp(1)));
        }
        LinearLayout row = new LinearLayout(getContext());
        grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView cell(LinearLayout row, String text, int column, boolean header) {
        TextView cell = card.text(text, 13, card.ink, header);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setMinHeight(card.dp(44));
        cell.setPadding(card.dp(10), card.dp(7), card.dp(8), card.dp(7));
        row.addView(cell, new LayoutParams(widths[column], LayoutParams.WRAP_CONTENT));
        return cell;
    }

    private void ripple(View view) {
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(0x40808080), null,
                card.shape(0xffffffff, 0, 0)));
    }

    private void edit(Object[] record, int column) {
        boolean numeric = record[column] instanceof Number;
        VisualField field = new VisualField(card, columns[column], numeric ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        field.setText(cellText(record[column]));
        field.selectAll();
        LinearLayout frame = new LinearLayout(getContext());
        frame.setPadding(card.dp(20), card.dp(10), card.dp(20), 0);
        frame.addView(field, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = card.dialog().setTitle(columns[column]).setView(frame)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create();
        field.requestFocus();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String text = field.getText().toString().trim();
            Object value = text;
            if (numeric && !text.isEmpty()) {
                try {
                    double number = Double.parseDouble(text);
                    if (!Double.isFinite(number) || Math.abs(number) > 1e15) throw new NumberFormatException();
                    value = number;
                } catch (NumberFormatException invalid) {
                    field.setError(card.local("请输入有效数字", "Enter a valid number"));
                    return;
                }
            }
            String rowLabel = VisualContext.shortText(cellText(record[0]), 100);
            record[column] = value;
            dialog.dismiss();
            render();
            if (card.hasContext()) {
                int row = Arrays.asList(rows).indexOf(record) + 1;
                card.recordContext(component, "cell/" + row + "/" + column, "table_edit", VisualContext.json(
                        "table", VisualCard.shortLabel(component), "row_number", row, "row_label", rowLabel,
                        "column", VisualContext.shortText(columns[column], 70), "value", VisualCard.contextValue(value)));
            }
        });
    }

    private void copy() {
        StringBuilder content = new StringBuilder(String.join("\t", columns));
        for (Object[] row : visible) {
            content.append('\n');
            for (int i = 0; i < row.length; i++) {
                if (i > 0) content.append('\t');
                content.append(cellText(row[i]).replace('\t', ' ').replace('\n', ' '));
            }
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Table", content.toString()));
    }

    private static String cellText(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof Double && ((Double) value) == Math.rint((Double) value)) {
            return Long.toString(((Double) value).longValue());
        }
        return value.toString();
    }

    private static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if ((a instanceof Number) != (b instanceof Number)) return a instanceof Number ? -1 : 1;
        return cellText(a).compareToIgnoreCase(cellText(b));
    }

    static void validate(JSONObject item) throws JSONException {
        JSONArray columns = VisualSpec.array(item.get("columns"), 1, 8);
        for (int i = 0; i < columns.length(); i++) VisualSpec.string(columns.get(i));
        JSONArray rows = VisualSpec.array(item.get("rows"), 1, 60);
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = VisualSpec.array(rows.get(i), columns.length(), columns.length());
            for (int j = 0; j < row.length(); j++) if (!row.isNull(j)) VisualSpec.scalar(row.get(j));
        }
        if (item.has("editable")) {
            JSONArray editing = VisualSpec.array(item.get("editable"), 0, columns.length());
            for (int i = 0; i < editing.length(); i++) {
                double column = VisualSpec.number(editing.get(i));
                VisualSpec.require(column == Math.rint(column) && column >= 0 && column < columns.length());
            }
        }
    }
}
