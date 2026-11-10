package local.visualize;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualInputs {
    private VisualInputs() {}

    static View input(VisualCard card, JSONObject item) {
        String title = item.optString("label"), id = item.optString("id"), kind = item.optString("kind");
        LinearLayout group = card.section(card.panel, 0);
        group.addView(card.text(title, 14, card.ink, true));
        if ("date".equals(kind) || "time".equals(kind)) {
            Button button = card.button("", false);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            card.append(group, button, LinearLayout.LayoutParams.WRAP_CONTENT, 8);
            String empty = "date".equals(kind) ? card.local("选择日期", "Choose date") : card.local("选择时间", "Choose time");
            card.onUpdate(() -> button.setText((card.value(id) == null ? empty : card.value(id)) + "  ▾"));
            button.setOnClickListener(view -> chooseDateTime(card, id, kind));
            return group;
        }
        boolean numeric = "number".equals(kind);
        VisualField field = new VisualField(card, item.optString("placeholder", title), numeric
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_TEXT);
        card.append(group, field, LinearLayout.LayoutParams.WRAP_CONTENT, 8);
        field.onText(text -> {
            if (card.isRefreshing()) return;
            Object value = numeric ? readNumber(text) : text;
            if (numeric && !text.trim().isEmpty() && !accepts(item, value)) {
                field.setError(card.local("请输入范围内的有效数字", "Enter a valid number in range"));
                value = null;
            } else field.setError(null);
            card.set(id, value);
        });
        card.onUpdate(() -> {
            Object value = card.value(id);
            String text = field.getText().toString();
            if (field.hasFocus() && VisualSpec.same(value, numeric ? readNumber(text) : text)) return;
            if (value == null && field.hasFocus() && !text.isEmpty()) return;
            String next = value instanceof Number ? numberText((Number) value) : value == null ? "" : value.toString();
            if (!text.equals(next)) {
                field.setText(next);
                field.setSelection(field.length());
                field.setError(null);
            }
        });
        return group;
    }

    private static void chooseDateTime(VisualCard card, String id, String kind) {
        String value = (String) card.value(id);
        Calendar today = Calendar.getInstance();
        if ("date".equals(kind)) {
            int year = value == null ? today.get(Calendar.YEAR) : Integer.parseInt(value.substring(0, 4));
            int month = value == null ? today.get(Calendar.MONTH) : Integer.parseInt(value.substring(5, 7)) - 1;
            int day = value == null ? today.get(Calendar.DAY_OF_MONTH) : Integer.parseInt(value.substring(8));
            DatePickerDialog picker = new DatePickerDialog(card.getContext(), card.dialogTheme,
                    (view, y, m, d) -> card.set(id, String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)),
                    year, month, day);
            today.clear();
            today.set(1900, Calendar.JANUARY, 1);
            picker.getDatePicker().setMinDate(today.getTimeInMillis());
            today.set(2100, Calendar.DECEMBER, 31);
            picker.getDatePicker().setMaxDate(today.getTimeInMillis());
            picker.show();
        } else {
            int hours = value == null ? today.get(Calendar.HOUR_OF_DAY) : Integer.parseInt(value.substring(0, 2));
            int minutes = value == null ? today.get(Calendar.MINUTE) : Integer.parseInt(value.substring(3));
            new TimePickerDialog(card.getContext(), card.dialogTheme,
                    (view, h, m) -> card.set(id, String.format(Locale.ROOT, "%02d:%02d", h, m)), hours, minutes,
                    android.text.format.DateFormat.is24HourFormat(card.getContext())).show();
        }
    }

    static View checklist(VisualCard card, JSONObject item) {
        String id = item.optString("id");
        LinearLayout group = card.section(card.panel, 0);
        group.addView(card.text(item.optString("label"), 14, card.ink, true));
        JSONArray options = item.optJSONArray("options");
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            Object value = option.opt("value");
            CheckBox box = new CheckBox(card.getContext());
            box.setText(option.optString("label"));
            box.setTextSize(14);
            box.setTextColor(card.ink);
            box.setMinHeight(card.dp(43));
            box.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{card.accent, card.secondary}));
            card.append(group, box, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
            card.onUpdate(() -> box.setChecked(VisualSpec.contains((JSONArray) card.value(id), value)));
            box.setOnCheckedChangeListener((view, checked) -> {
                if (card.isRefreshing()) return;
                JSONArray selected = (JSONArray) card.value(id), next = new JSONArray();
                for (int j = 0; j < selected.length(); j++) {
                    if (!VisualSpec.same(selected.opt(j), value)) next.put(selected.opt(j));
                }
                if (checked) next.put(value);
                card.set(id, next);
            });
        }
        return group;
    }

    private static Double readNumber(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) && Math.abs(value) <= 1e15 ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String numberText(Number value) {
        double number = value.doubleValue();
        return number == Math.rint(number) ? Long.toString((long) number)
                : java.math.BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    static boolean accepts(JSONObject item, Object value) {
        switch (item.optString("kind")) {
            case "text": return value instanceof String && ((String) value).length() <= 240;
            case "number":
                if (value == JSONObject.NULL) return true;
                if (!(value instanceof Number)) return false;
                double number = ((Number) value).doubleValue();
                return number >= item.optDouble("min", -1e15) && number <= item.optDouble("max", 1e15);
            case "time": return value == JSONObject.NULL || value instanceof String
                    && ((String) value).matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]");
            case "date":
                if (value == JSONObject.NULL) return true;
                if (!(value instanceof String) || !((String) value).matches("(?:19[0-9]{2}|20[0-9]{2}|2100)-[0-9]{2}-[0-9]{2}")) return false;
                String[] parts = ((String) value).split("-");
                Calendar date = Calendar.getInstance();
                date.clear();
                date.setLenient(false);
                date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                try { date.getTimeInMillis(); return true; }
                catch (IllegalArgumentException invalid) { return false; }
            default: return false;
        }
    }

    static void validate(JSONObject item) throws JSONException {
        if (item.has("placeholder")) VisualSpec.string(item.get("placeholder"));
        if ("number".equals(item.optString("kind"))) {
            if (item.has("min")) VisualSpec.number(item.get("min"));
            if (item.has("max")) VisualSpec.number(item.get("max"));
            VisualSpec.require(item.optDouble("min", -1e15) <= item.optDouble("max", 1e15));
        }
    }
}
