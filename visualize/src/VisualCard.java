package local.visualize;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class VisualCard extends LinearLayout {
    private final Map<String, Object> state = new HashMap<>();
    private final List<Runnable> updates = new ArrayList<>();
    private final List<JSONObject> readouts = new ArrayList<>();
    private final Map<JSONObject, Integer> componentKeys = new IdentityHashMap<>();
    final int ink, secondary, accent, border, panel, success, error;
    final int chartSurface, dialogTheme;
    final int surface;
    private final int pressed, buttonInk;
    private boolean refreshing;
    private VisualSpec spec;
    private VisualContext.Scope scope;
    private LinearLayout contextFooter;

    public VisualCard(Context context, boolean dark) {
        super(context);
        dialogTheme = dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
        ink = dark ? 0xfff1f1f2 : 0xff202124;
        secondary = dark ? 0xffadb0b7 : 0xff555c67;
        accent = dark ? 0xff77b8ff : 0xff175fc3;
        border = dark ? 0xff34363b : 0xffe2e4e8;
        surface = dark ? 0xff17181b : 0xffffffff;
        panel = dark ? 0xff222327 : 0xfff5f6f7;
        chartSurface = dark ? 0xff24262b : 0xfffafafb;
        pressed = dark ? 0x24ffffff : 0x19000000;
        buttonInk = dark ? 0xff17181b : 0xffffffff;
        success = dark ? 0xff66d79e : 0xff087542;
        error = dark ? 0xffff9b99 : 0xffaa3537;
        setOrientation(VERTICAL);
    }

    public void bind(VisualSpec next, VisualContext.Scope nextScope) {
        if (next.readOnly) nextScope = null;
        if (spec != null && spec.status == next.status && Objects.equals(scope, nextScope)
                && spec.root.toString().equals(next.root.toString())) {
            updateContextFooter();
            return;
        }
        spec = next;
        scope = nextScope;
        setBackground(next.readOnly ? null : shape(surface, 20, border));
        int inset = next.readOnly ? 0 : dp(16);
        setPadding(inset, inset, inset, inset);
        removeAllViews();
        updates.clear();
        readouts.clear();
        componentKeys.clear();
        contextFooter = null;
        resetState();
        String title = spec.root.optString("title");
        if (!next.readOnly || !title.isEmpty()) {
            TextView heading = text(title, next.readOnly ? 19 : 21, ink, true);
            add(heading, LayoutParams.WRAP_CONTENT, 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        }
        String subtitle = spec.root.optString("subtitle");
        if (!subtitle.isEmpty()) add(text(subtitle, 14, secondary, false), LayoutParams.WRAP_CONTENT, 5);
        JSONArray items = spec.root.optJSONArray("items");
        if (items != null) renderItems(this, items);
        showStatus(next.status);
        if (scope != null) addContextFooter();
        refresh();
    }

    private void showStatus(VisualSpec.Status status) {
        String message;
        switch (status) {
            case INCOMPLETE:
                message = local("可视化内容尚未完整", "The visualization is incomplete.");
                break;
            case PARTIAL:
                message = local("已展示可读取的可视化内容。", "The readable parts of the visualization are shown.");
                break;
            case UNAVAILABLE:
                message = local("本条回复的可视化暂无法显示。", "The visualization in this reply could not be displayed.");
                break;
            default:
                return;
        }
        TextView notice = text(message, 13, secondary, false);
        notice.setPadding(dp(12), dp(9), dp(12), dp(9));
        notice.setBackground(shape(panel, 12, 0));
        add(notice, LayoutParams.WRAP_CONTENT, 8);
    }

    private void addContextFooter() {
        contextFooter = section(panel, 0);
        contextFooter.addView(text(local("交互结果会随本对话的下一条消息发送。", "New results are included in the next message in this chat."),
                12, secondary, false));
        Button clear = button(local("清除待发送结果", "Clear pending results"), false);
        clear.setOnClickListener(view -> {
            VisualContext.clear(scope);
            updateContextFooter();
        });
        append(contextFooter, clear, LayoutParams.WRAP_CONTENT, 5);
        add(contextFooter, LayoutParams.WRAP_CONTENT, 12);
        updateContextFooter();
    }

    private void updateContextFooter() {
        if (contextFooter != null) contextFooter.setVisibility(VisualContext.hasUnsent(scope) ? VISIBLE : GONE);
    }

    boolean hasContext() { return scope != null; }

    boolean isReadOnly() { return spec.readOnly; }

    void recordContext(JSONObject item, String slot, String type, JSONObject payload) {
        if (scope == null) return;
        String key = item == null ? slot : componentKeys.get(item) + "/" + slot;
        VisualContext.record(scope, key, spec.root.optString("title"), type, payload);
        updateContextFooter();
    }

    void renderItems(LinearLayout target, JSONArray items) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONObject nextItem = items.optJSONObject(i + 1);
            if (isMetric(item) && isMetric(nextItem)) {
                append(target, new MetricPair(metric(item), metric(nextItem)), LayoutParams.WRAP_CONTENT, 10);
                i++;
            } else append(target, createItem(item), LayoutParams.WRAP_CONTENT, 10);
        }
    }

    private boolean isMetric(JSONObject item) {
        return item != null && "metric".equals(item.optString("type"));
    }

    private void resetState() {
        state.clear();
        for (Map.Entry<String, JSONObject> entry : spec.controls.entrySet()) {
            Object initial = entry.getValue().opt("initial");
            state.put(entry.getKey(), initial == JSONObject.NULL ? null : initial);
        }
    }

    private View createItem(JSONObject item) {
        componentKeys.put(item, componentKeys.size());
        String title = item.optString("label");
        String id = item.optString("id");
        switch (item.optString("type")) {
            case "text":
                TextView text = text(item.optString("text"), 14, secondary, false);
                if (item.has("value")) updates.add(() -> text.setText(display(eval(item.opt("value")), 2, "")));
                return text;
            case "metric":
                return metric(item);
            case "slider":
                return slider(item, title, id);
            case "select":
                return select(item, title, id);
            case "toggle":
                Switch toggle = new Switch(getContext());
                toggle.setText(title);
                toggle.setTextColor(ink);
                toggle.setTextSize(14);
                toggle.setMinHeight(dp(46));
                toggle.setPadding(dp(12), dp(4), dp(12), dp(4));
                toggle.setSwitchPadding(dp(12));
                int[][] toggleStates = {{android.R.attr.state_checked}, {}};
                toggle.setThumbTintList(new ColorStateList(toggleStates, new int[]{0xffffffff, secondary}));
                toggle.setTrackTintList(new ColorStateList(toggleStates, new int[]{accent, border}));
                toggle.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), shape(panel, 12, 0), null));
                updates.add(() -> toggle.setChecked(Boolean.TRUE.equals(state.get(id))));
                toggle.setOnCheckedChangeListener((button, checked) -> set(id, checked));
                return toggle;
            case "button":
                Button button = button(title, !"reset".equals(item.optString("action")));
                button.setOnClickListener(view -> {
                    if ("reset".equals(item.optString("action"))) {
                        View focused = findFocus();
                        if (focused != null) focused.clearFocus();
                        resetState();
                        refresh();
                        recordControls();
                    } else {
                        String target = item.optString("target");
                        Object next = eval(item.opt("value"));
                        try {
                            if (VisualSpec.accepts(spec.controls.get(target), next)) set(target, next);
                        } catch (JSONException | IllegalArgumentException invalid) {
                            // An expression may produce an unavailable or out-of-range value.
                        }
                    }
                });
                return button;
            case "chart":
                return VisualChart.create(this, item);
            case "quiz":
                return new VisualQuiz(this, item);
            case "table":
                return isReadOnly() ? VisualStatic.table(this, item) : new VisualTable(this, item);
            case "summary":
                return VisualStatic.summary(this, item);
            case "input":
                return VisualInputs.input(this, item);
            case "checklist":
                return VisualInputs.checklist(this, item);
            case "group":
                return VisualSections.group(this, item);
            case "deck":
                return isReadOnly() ? VisualStatic.sequence(this, item) : VisualSections.deck(this, item);
            case "progress":
                return progress(item);
            case "gantt":
                return VisualPlan.gantt(this, item);
            case "diagram":
                return VisualPlan.diagram(this, item);
            case "map":
                return VisualMap.create(this, item);
            case "emoji": case "image": case "gallery":
                return VisualMedia.create(this, item);
            case "callout": case "quote": case "facts": case "tiles": case "ranking": case "status": case "rating":
                return VisualShowcase.create(this, item);
            case "palette": case "board": case "diff":
                return VisualExplore.create(this, item);
            case "calendar": case "tree": case "formula":
                return VisualReference.create(this, item);
            default:
                throw new AssertionError("Validated component expected");
        }
    }

    private View progress(JSONObject item) {
        readouts.add(item);
        LinearLayout field = section(panel, 0);
        TextView readout = text("", 13, secondary, true);
        readout.setGravity(Gravity.END);
        field.addView(split(text(item.optString("label"), 14, ink, true), readout));
        ProgressBar progress = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressDrawable(track(border, accent, 6));
        progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        append(field, progress, dp(6), 10);
        onUpdate(() -> {
            Object value = eval(item.opt("value"));
            Object max = item.has("max") ? eval(item.opt("max")) : 100;
            double end = max instanceof Number ? ((Number) max).doubleValue() : 0;
            double current = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
            boolean available = Double.isFinite(current) && end > 0;
            progress.setProgress(available ? (int) Math.round(Math.max(0, Math.min(1, current / end)) * 1000) : 0);
            readout.setText(available ? display(value, 2, "") + " / " + display(max, 2, item.optString("unit")) : "—");
        });
        return field;
    }

    private View metric(JSONObject item) {
        readouts.add(item);
        LinearLayout tile = section(panel, 0);
        tile.addView(text(item.optString("label"), 13, secondary, false));
        TextView value = text("", 24, ink, true);
        append(tile, value, LayoutParams.WRAP_CONTENT, 5);
        updates.add(() -> value.setText(display(eval(item.opt("value")), item.optInt("digits", 2),
                item.optString("unit"))));
        return tile;
    }

    private View slider(JSONObject item, String title, String id) {
        LinearLayout field = section(panel, 0);
        TextView current = text("", 18, ink, true);
        current.setGravity(Gravity.END);
        field.addView(split(text(title, 14, ink, true), current));
        double min = item.optDouble("min");
        double max = item.optDouble("max");
        double step = item.optDouble("step");
        String unit = item.optString("unit");
        SeekBar slider = new SeekBar(getContext());
        slider.setMax((int) Math.round((max - min) / step));
        slider.setContentDescription(title);
        slider.setSplitTrack(false);
        slider.setProgressDrawable(track(border, secondary, 5));
        GradientDrawable thumb = shape(ink, 20, 0);
        thumb.setSize(dp(16), dp(16));
        slider.setThumb(thumb);
        slider.setPadding(dp(8), 0, dp(8), 0);
        append(field, slider, dp(42), 2);
        TextView upper = text(display(max, 6, unit), 12, secondary, false);
        upper.setGravity(Gravity.END);
        field.addView(split(text(display(min, 6, unit), 12, secondary, false), upper));
        updates.add(() -> {
            double value = ((Number) state.get(id)).doubleValue();
            slider.setProgress((int) Math.round((value - min) / step));
            current.setText(display(value, 6, unit));
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                bar.getParent().requestDisallowInterceptTouchEvent(true);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                bar.getParent().requestDisallowInterceptTouchEvent(false);
            }
            @Override public void onProgressChanged(SeekBar bar, int tick, boolean fromUser) {
                if (fromUser) set(id, min + step * tick);
            }
        });
        return field;
    }

    private View select(JSONObject item, String title, String id) {
        LinearLayout field = section(panel, 0);
        field.addView(text(title, 13, secondary, false));
        Button choice = button("", false);
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        append(field, choice, LayoutParams.WRAP_CONTENT, 7);
        JSONArray options = item.optJSONArray("options");
        String[] labels = new String[options.length()];
        for (int i = 0; i < options.length(); i++) labels[i] = options.optJSONObject(i).optString("label");
        updates.add(() -> {
            for (int i = 0; i < options.length(); i++) {
                if (VisualSpec.same(options.optJSONObject(i).opt("value"), state.get(id))) {
                    choice.setText(labels[i] + "  ▾");
                    break;
                }
            }
        });
        choice.setOnClickListener(view -> dialog()
                .setTitle(title).setItems(labels, (dialog, index) -> set(id, options.optJSONObject(index).opt("value")))
                .setNegativeButton(android.R.string.cancel, null).show());
        return field;
    }

    Object eval(Object expr) { return VisualSpec.evaluate(expr, state); }
    Object value(String id) { return state.get(id); }
    void onUpdate(Runnable action) { updates.add(action); }
    boolean isRefreshing() { return refreshing; }

    void set(String id, Object value) {
        if (refreshing || VisualSpec.same(state.get(id), value)) return;
        state.put(id, value);
        refresh();
        recordControls();
    }

    private void recordControls() {
        if (scope == null || spec.controls.isEmpty()) return;
        JSONArray controls = new JSONArray(), outputs = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : new TreeMap<>(spec.controls).entrySet()) {
            String id = entry.getKey();
            JSONObject item = entry.getValue();
            Object value = state.get(id);
            controls.put(VisualContext.json("id", id, "label", shortLabel(item), "value", contextValue(value),
                    "unit", VisualContext.shortText(item.optString("unit"), 24)));
        }
        for (JSONObject item : readouts) {
            outputs.put(VisualContext.json("label", shortLabel(item), "value", contextValue(eval(item.opt("value"))),
                    "unit", VisualContext.shortText(item.optString("unit"), 24)));
        }
        recordContext(null, "controls", "control_values", VisualContext.json("controls", controls, "computed_values", outputs));
    }

    static String shortLabel(JSONObject item) { return VisualContext.shortText(item.optString("label"), 72); }

    static Object contextValue(Object value) {
        if (value instanceof String) return VisualContext.shortText((String) value, 120);
        if (value instanceof JSONArray) {
            JSONArray from = (JSONArray) value, clipped = new JSONArray();
            for (int i = 0; i < from.length(); i++) clipped.put(contextValue(from.opt(i)));
            return clipped;
        }
        return value;
    }

    private void refresh() {
        refreshing = true;
        try { for (Runnable update : updates) update.run(); }
        finally { refreshing = false; }
    }

    int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    String local(String zh, String en) {
        return getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh") ? zh : en;
    }

    android.app.AlertDialog.Builder dialog() {
        return new android.app.AlertDialog.Builder(getContext(), dialogTheme);
    }

    TextView text(String text, int size, int color, boolean emphasis) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (emphasis) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    Button button(String title, boolean filled) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(46));
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        styleButton(button, filled);
        return button;
    }

    void styleButton(Button button, boolean filled) {
        int foreground = filled ? buttonInk : ink;
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{secondary, foreground}));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, shape(panel, 30, border));
        background.addState(new int[0], new RippleDrawable(ColorStateList.valueOf(filled ? 0x44888888 : pressed),
                shape(filled ? ink : surface, 30, filled ? 0 : border), null));
        button.setBackground(background);
    }

    GradientDrawable shape(int color, int radius, int outline) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (outline != 0) drawable.setStroke(dp(1), outline);
        return drawable;
    }

    LayerDrawable track(int base, int fill, int height) {
        LayerDrawable drawable = new LayerDrawable(new android.graphics.drawable.Drawable[]{shape(base, 20, 0),
                new ClipDrawable(shape(fill, 20, 0), Gravity.START, ClipDrawable.HORIZONTAL)});
        drawable.setId(0, android.R.id.background);
        drawable.setId(1, android.R.id.progress);
        for (int i = 0; i < drawable.getNumberOfLayers(); i++) {
            drawable.setLayerHeight(i, dp(height));
            drawable.setLayerGravity(i, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL);
        }
        return drawable;
    }

    LinearLayout section(int background, int outline) {
        LinearLayout group = new LinearLayout(getContext());
        group.setOrientation(VERTICAL);
        group.setPadding(dp(12), dp(11), dp(12), dp(11));
        group.setBackground(shape(background, 13, outline));
        return group;
    }

    LinearLayout split(TextView left, TextView right) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams leftParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        leftParams.setMarginEnd(dp(8));
        row.addView(left, leftParams);
        row.addView(right, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void add(View view, int height, int margin) {
        append(this, view, height, margin);
    }

    void append(LinearLayout group, View view, int height, int margin) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, height);
        params.topMargin = dp(margin);
        group.addView(view, params);
    }

    private final class MetricPair extends LinearLayout {
        MetricPair(View first, View second) {
            super(VisualCard.this.getContext());
            setOrientation(VERTICAL);
            append(this, first, LayoutParams.WRAP_CONTENT, 0);
            append(this, second, LayoutParams.WRAP_CONTENT, 8);
        }

        @Override protected void onMeasure(int width, int height) {
            boolean columns = MeasureSpec.getSize(width) >= dp(250)
                    && getResources().getConfiguration().fontScale <= 1.2f;
            setOrientation(columns ? HORIZONTAL : VERTICAL);
            for (int i = 0; i < 2; i++) {
                LayoutParams params = (LayoutParams) getChildAt(i).getLayoutParams();
                params.width = columns ? 0 : LayoutParams.MATCH_PARENT;
                params.weight = columns ? 1 : 0;
                boolean spaced = columns && i == 1;
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                params.leftMargin = spaced && !rtl ? dp(8) : 0;
                params.rightMargin = spaced && rtl ? dp(8) : 0;
                params.topMargin = !columns && i == 1 ? dp(8) : 0;
            }
            super.onMeasure(width, height);
        }
    }

    String display(Object value, int digits, String unit) {
        if (value == null) return "—";
        String result;
        if (value instanceof Number) {
            NumberFormat format = NumberFormat.getNumberInstance();
            format.setMaximumFractionDigits(digits);
            result = format.format(value);
        } else if (value instanceof Boolean) result = (Boolean) value ? "是" : "否";
        else result = value.toString();
        return unit.isEmpty() ? result : result + " " + unit;
    }
}
