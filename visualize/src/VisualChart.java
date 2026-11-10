package local.visualize;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualChart extends View {
    private static final int[] COLORS = {0xff3a8de0, 0xff9465e0, 0xff30a68b, 0xffdf9252,
            0xffd26288, 0xff859646, 0xff5d9db1, 0xffa07d62};
    private final VisualCard card;
    private final JSONObject component;
    private final String kind, title, unit;
    private final String[] labels, names;
    private final JSONArray series;
    private final TextView detail;
    private final double[][] values;
    private final double[] xValues;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint measure = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dashed;
    private final RectF plot = new RectF();
    private final boolean radial, breakdown, readOnly;
    private double low, high, step, xMin, xMax, positiveTotal;
    private boolean hasValues;
    private int selected = -1;
    private float downX, downY;

    static View create(VisualCard card, JSONObject item) {
        LinearLayout frame = card.section(card.chartSurface, card.border);
        frame.addView(card.text(item.optString("label"), 14, card.ink, true));
        TextView detail = card.text("", 13, card.secondary, false);
        VisualChart chart = new VisualChart(card, item, detail);
        if (chart.readOnly && chart.selected >= 0) card.append(frame, detail, LinearLayout.LayoutParams.WRAP_CONTENT, 8);
        int height = "heatmap".equals(chart.kind) ? Math.min(250, 56 + chart.names.length * 29) : 193;
        card.append(frame, chart, card.dp(height), 8);
        if (chart.breakdown) {
            for (int i = 0; i < chart.labels.length; i += 2) {
                LinearLayout row = new LinearLayout(card.getContext());
                for (int j = i; j < Math.min(chart.labels.length, i + 2); j++) {
                    final int index = j;
                    TextView label = chart.legend(chart.labels[j], j);
                    if (!chart.readOnly) label.setOnClickListener(view -> chart.choose(index));
                    row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                }
                card.append(frame, row, LinearLayout.LayoutParams.WRAP_CONTENT, 4);
            }
        } else if (!"heatmap".equals(chart.kind)) {
            for (int i = 0; i < chart.names.length; i++) {
                card.append(frame, chart.legend(chart.names[i], i), LinearLayout.LayoutParams.WRAP_CONTENT, 3);
            }
        }
        if (!chart.readOnly) card.append(frame, detail, LinearLayout.LayoutParams.WRAP_CONTENT, 8);
        String caption = item.optString("caption");
        if (!caption.isEmpty()) card.append(frame, card.text(caption, 12, card.secondary, false),
                LinearLayout.LayoutParams.WRAP_CONTENT, 7);
        card.onUpdate(chart::update);
        return frame;
    }

    private VisualChart(VisualCard card, JSONObject item, TextView detail) {
        super(card.getContext());
        this.card = card;
        component = item;
        readOnly = card.isReadOnly();
        Object highlight = highlightIndex(item);
        selected = highlight instanceof Number ? ((Number) highlight).intValue() : -1;
        this.detail = detail;
        kind = item.optString("kind");
        title = item.optString("label");
        unit = item.optString("unit");
        radial = "pie".equals(kind) || "donut".equals(kind);
        breakdown = radial || "funnel".equals(kind);
        labels = strings(item.optJSONArray("labels"));
        series = item.optJSONArray("series");
        names = new String[series.length()];
        for (int i = 0; i < names.length; i++) names[i] = series.optJSONObject(i).optString("label");
        values = new double[names.length][labels.length];
        xValues = new double[labels.length];
        JSONArray x = item.optJSONArray("x");
        xMin = Double.POSITIVE_INFINITY;
        xMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < xValues.length; i++) {
            xValues[i] = x == null ? i : x.optDouble(i);
            xMin = Math.min(xMin, xValues[i]);
            xMax = Math.max(xMax, xValues[i]);
        }
        if (xMin == xMax) { xMin -= 1; xMax += 1; }
        dashed = new DashPathEffect(new float[]{card.dp(3), card.dp(3)}, 0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setClickable(!readOnly);
    }

    private TextView legend(String name, int index) {
        TextView view = card.text("", 12, card.secondary, false);
        android.text.SpannableString label = new android.text.SpannableString("●  " + name);
        label.setSpan(new android.text.style.ForegroundColorSpan(color(index)), 0, 1, 0);
        view.setText(label);
        if (breakdown) view.setMinHeight(card.dp(36));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void update() {
        boolean changed = !hasValues;
        for (int s = 0; s < values.length; s++) {
            JSONArray data = series.optJSONObject(s).optJSONArray("values");
            for (int i = 0; i < labels.length; i++) {
                Object value = card.eval(data.opt(i));
                double next = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
                changed |= Double.compare(values[s][i], next) != 0;
                values[s][i] = next;
            }
        }
        if (!changed) return;
        hasValues = true;
        low = Double.POSITIVE_INFINITY;
        high = Double.NEGATIVE_INFINITY;
        positiveTotal = 0;
        StringBuilder summary = new StringBuilder(title);
        for (int s = 0; s < values.length; s++) {
            summary.append(" · ").append(names[s]);
            for (int i = 0; i < labels.length; i++) {
                double y = values[s][i];
                if (Double.isFinite(y)) {
                    low = Math.min(low, y);
                    high = Math.max(high, y);
                    positiveTotal += Math.max(0, y);
                }
                summary.append(" · ").append(labels[i]).append(' ').append(number(y, unit));
            }
        }
        if ("stacked".equals(kind)) {
            for (int i = 0; i < labels.length; i++) {
                double positive = 0, negative = 0;
                for (double[] row : values) {
                    if (!Double.isFinite(row[i])) continue;
                    if (row[i] >= 0) positive += row[i];
                    else negative += row[i];
                }
                low = Math.min(low, negative);
                high = Math.max(high, positive);
            }
        }
        if (!Double.isFinite(low)) { low = 0; high = 1; }
        if (bars() || "area".equals(kind) || "radar".equals(kind) || breakdown) {
            low = Math.min(0, low);
            high = Math.max(0, high);
        }
        if (low == high) { double pad = Math.max(1, Math.abs(low) * 0.1); low -= pad; high += pad; }
        if (!breakdown && !"heatmap".equals(kind) && !"radar".equals(kind)) {
            double rough = (high - low) / 4;
            double base = Math.pow(10, Math.floor(Math.log10(rough)));
            double normalized = rough / base;
            step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * base;
            if (!Double.isFinite(step) || step <= 0) step = high - low;
            low = Math.floor(low / step) * step;
            high = Math.ceil(high / step) * step;
        }
        setContentDescription(summary);
        refreshDetail();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(card.dp(11));
        paint.setStrokeCap(Paint.Cap.ROUND);
        plot.set(card.dp(42), card.dp(10), getWidth() - card.dp(10), getHeight() - card.dp(24));
        if (plot.width() <= 1 || plot.height() <= 1) return;
        if (radial) drawSlices(canvas);
        else if ("radar".equals(kind)) drawRadar(canvas);
        else if ("heatmap".equals(kind)) drawHeatmap(canvas);
        else if ("funnel".equals(kind)) drawFunnel(canvas);
        else drawAxes(canvas);
    }

    private void drawAxes(Canvas canvas) {
        paint.setStrokeWidth(card.dp(1));
        int ticks = Math.min(8, (int) Math.round((high - low) / step));
        for (int i = 0; i <= ticks; i++) {
            double value = low + step * i;
            float y = y(value);
            paint.setColor(card.border);
            if (i != 0) paint.setPathEffect(dashed);
            canvas.drawLine(plot.left, y, plot.right, y, paint);
            paint.setPathEffect(null);
            paint.setColor(card.secondary);
            String label = clip(axisNumber(value), plot.left - card.dp(5));
            canvas.drawText(label, plot.left - card.dp(5) - paint.measureText(label), y + card.dp(4), paint);
        }
        if ("scatter".equals(kind)) {
            for (int i = 0; i < 3; i++) {
                double value = xMin + (xMax - xMin) * i / 2;
                axisLabel(canvas, axisNumber(value), plot.left + plot.width() * i / 2, plot.width() / 3);
            }
        } else drawCategoryLabels(canvas);
        if (selected >= 0) {
            paint.setColor(card.accent);
            paint.setPathEffect(dashed);
            canvas.drawLine(x(selected), plot.top, x(selected), plot.bottom, paint);
            paint.setPathEffect(null);
        }
        if (bars()) { drawBars(canvas); return; }
        for (int s = 0; s < values.length; s++) {
            float previousX = 0, previousY = Float.NaN;
            for (int i = 0; i < labels.length; i++) {
                float x = x(i), y = y(values[s][i]);
                if (Float.isFinite(y)) {
                    int color = color(s);
                    if (Float.isFinite(previousY) && !"scatter".equals(kind)) {
                        if ("area".equals(kind)) {
                            paint.setColor((color & 0x00ffffff) | 0x42000000);
                            Path area = new Path();
                            area.moveTo(previousX, y(0)); area.lineTo(previousX, previousY);
                            area.lineTo(x, y); area.lineTo(x, y(0)); area.close();
                            canvas.drawPath(area, paint);
                        }
                        paint.setColor(color);
                        paint.setStrokeWidth(card.dp(2));
                        canvas.drawLine(previousX, previousY, x, y, paint);
                    }
                    paint.setColor(color);
                    if (labels.length <= 12 || i == selected || "scatter".equals(kind)) {
                        canvas.drawCircle(x, y, card.dp(i == selected ? 5 : 3), paint);
                    }
                }
                previousX = x; previousY = y;
            }
        }
    }

    private void drawBars(Canvas canvas) {
        boolean stacked = "stacked".equals(kind);
        boolean histogram = "histogram".equals(kind);
        float band = plot.width() / labels.length;
        float width = histogram ? band : Math.min(card.dp(46), band * .74f / (stacked ? 1 : values.length));
        for (int i = 0; i < labels.length; i++) {
            double positive = 0, negative = 0;
            for (int s = 0; s < values.length; s++) {
                double value = values[s][i];
                if (!Double.isFinite(value)) continue;
                double base = stacked ? (value >= 0 ? positive : negative) : 0;
                if (value >= 0) positive += value;
                else negative += value;
                float left = x(i) + (stacked ? -.5f : s - values.length / 2f) * width;
                float y1 = y(base), y2 = y(base + value), gap = Math.min(card.dp(histogram ? .5f : 1), width / 4);
                paint.setColor(color(s));
                if (selected >= 0 && i != selected) paint.setAlpha(160);
                canvas.drawRoundRect(left + gap, Math.min(y1, y2), left + width - gap, Math.max(y1, y2),
                        card.dp(histogram ? 0 : stacked ? 2 : 4), card.dp(histogram ? 0 : stacked ? 2 : 4), paint);
                paint.setAlpha(255);
            }
        }
    }

    private void drawCategoryLabels(Canvas canvas) {
        int count = Math.min(labels.length, plot.width() >= card.dp(218) ? 4 : 3);
        for (int i = 0; i < count; i++) {
            int index = Math.round((float) i * (labels.length - 1) / (count - 1));
            axisLabel(canvas, labels[index], x(index), plot.width() / count);
        }
    }

    private void axisLabel(Canvas canvas, String label, float center, float width) {
        paint.setColor(card.secondary);
        String text = clip(label, width - card.dp(4));
        float measured = paint.measureText(text);
        float x = Math.max(plot.left, Math.min(plot.right - measured, center - measured / 2));
        canvas.drawText(text, x, plot.bottom + card.dp(16), paint);
    }

    private void drawSlices(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - card.dp(7);
        if (positiveTotal <= 0) { empty(canvas, cx, cy); return; }
        boolean donut = "donut".equals(kind);
        float ring = radius * .38f;
        float arcRadius = donut ? radius - ring / 2 : radius;
        RectF bounds = new RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);
        float start = -90;
        for (int i = 0; i < labels.length; i++) {
            float sweep = slice(i);
            if (sweep <= 0) continue;
            paint.setColor(color(i));
            if (selected >= 0 && selected != i) paint.setAlpha(135);
            if (donut) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(ring); paint.setStrokeCap(Paint.Cap.BUTT); }
            float gap = sweep < 359.9f ? Math.min(2, sweep / 3) : 0;
            canvas.drawArc(bounds, start + gap / 2, sweep - gap, !donut, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            start += sweep;
        }
        if (donut) {
            paint.setColor(card.ink);
            paint.setTextSize(card.dp(18));
            centered(canvas, selected >= 0 ? number(values[0][selected], unit) : number(positiveTotal, unit), cx, cy + card.dp(6),
                    Math.max(1, (radius - ring) * 1.7f));
        }
    }

    private void drawRadar(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.max(card.dp(14), Math.min(cx - card.dp(38), cy - card.dp(21)));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(card.dp(1));
        paint.setColor(card.border);
        for (int ring = 1; ring <= 4; ring++) canvas.drawPath(polygon(cx, cy, radius * ring / 4, null), paint);
        for (int s = 0; s < values.length; s++) {
            Path shape = polygon(cx, cy, radius, values[s]);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((color(s) & 0x00ffffff) | 0x30000000);
            canvas.drawPath(shape, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color(s));
            paint.setStrokeWidth(card.dp(2));
            canvas.drawPath(shape, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < labels.length; i++) {
            double angle = angle(i);
            float x = cx + (float) Math.cos(angle) * (radius + card.dp(23));
            float y = cy + (float) Math.sin(angle) * (radius + card.dp(9));
            paint.setColor(i == selected ? card.accent : card.secondary);
            centered(canvas, labels[i], Math.max(card.dp(27), Math.min(getWidth() - card.dp(27), x)),
                    y + card.dp(4), card.dp(53));
        }
    }

    private Path polygon(float cx, float cy, float radius, double[] series) {
        Path path = new Path();
        for (int i = 0; i < labels.length; i++) {
            double value = series == null ? 1 : Double.isFinite(series[i]) ? (series[i] - low) / (high - low) : 0;
            float x = cx + (float) (Math.cos(angle(i)) * radius * value);
            float y = cy + (float) (Math.sin(angle(i)) * radius * value);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    private void drawHeatmap(Canvas canvas) {
        float width = plot.width() / labels.length, height = plot.height() / names.length;
        float gap = Math.min(card.dp(2), width / 6);
        for (int s = 0; s < names.length; s++) {
            float top = plot.top + height * s;
            paint.setColor(card.secondary);
            String row = clip(names[s], plot.left - card.dp(5));
            canvas.drawText(row, plot.left - card.dp(5) - paint.measureText(row), top + height / 2 + card.dp(4), paint);
            for (int i = 0; i < labels.length; i++) {
                float left = plot.left + width * i;
                double value = values[s][i];
                paint.setColor(Double.isFinite(value) ? blend(card.panel, card.accent, .16 + .84 * (value - low) / (high - low))
                        : card.border);
                canvas.drawRoundRect(left + gap, top + gap, left + width - gap, top + height - gap, card.dp(3), card.dp(3), paint);
                if (i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(card.dp(1));
                    paint.setColor(card.ink);
                    canvas.drawRoundRect(left + gap, top + gap, left + width - gap, top + height - gap, card.dp(3), card.dp(3), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
        drawCategoryLabels(canvas);
    }

    private void drawFunnel(Canvas canvas) {
        if (high <= 0) { empty(canvas, getWidth() / 2f, getHeight() / 2f); return; }
        float middle = getWidth() / 2f, band = (getHeight() - card.dp(8)) / (float) labels.length;
        for (int i = 0; i < labels.length; i++) {
            double value = values[0][i];
            if (!Double.isFinite(value) || value <= 0) continue;
            float half = (float) ((middle - card.dp(8)) * value / high);
            float top = card.dp(4) + band * i;
            paint.setColor(color(i));
            if (selected >= 0 && selected != i) paint.setAlpha(140);
            canvas.drawRoundRect(middle - half, top, middle + half, top + band - card.dp(3), card.dp(5), card.dp(5), paint);
            paint.setAlpha(255);
            if (half >= card.dp(27)) {
                paint.setColor(0xff101215);
                centered(canvas, axisNumber(value), middle, top + band / 2 + card.dp(3), half * 1.8f);
            }
        }
    }

    private void empty(Canvas canvas, float x, float y) {
        paint.setColor(card.secondary);
        centered(canvas, card.local("暂无可显示的数据", "No values to display"), x, y, getWidth());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (readOnly) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float x = event.getX(), y = event.getY();
            if (Math.hypot(x - downX, y - downY) <= ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                int index = hit(x, y);
                if (index >= 0) { choose(index); performClick(); }
            }
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    private int hit(float x, float y) {
        if (radial || "radar".equals(kind)) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (radial && Math.hypot(x - cx, y - cy) > Math.min(cx, cy)) return -1;
            double degrees = (Math.toDegrees(Math.atan2(y - cy, x - cx)) + 450) % 360;
            if ("radar".equals(kind)) return (int) Math.round(degrees * labels.length / 360) % labels.length;
            for (int i = 0; i < labels.length; i++) {
                degrees -= slice(i);
                if (degrees < 0) return i;
            }
            return -1;
        }
        if ("funnel".equals(kind)) return Math.max(0, Math.min(labels.length - 1, (int) (y / getHeight() * labels.length)));
        int nearest = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < labels.length; i++) {
            double candidate = Math.abs(x - x(i));
            if ("scatter".equals(kind)) {
                candidate = Double.POSITIVE_INFINITY;
                for (double[] row : values) {
                    if (Double.isFinite(row[i])) candidate = Math.min(candidate, Math.hypot(x - x(i), y - y(row[i])));
                }
            }
            if (candidate < distance) { distance = candidate; nearest = i; }
        }
        return nearest;
    }

    private void choose(int index) {
        selected = index;
        refreshDetail();
        invalidate();
        if (!card.hasContext()) return;
        JSONArray inspected = new JSONArray();
        for (int i = 0; i < names.length; i++) {
            double value = values[i][index];
            inspected.put(VisualContext.json("series", names[i], "value", Double.isFinite(value) ? value : null));
        }
        card.recordContext(component, "point", "chart_selection", VisualContext.json("chart", VisualCard.shortLabel(component),
                "kind", kind, "category", labels[index], "x", "scatter".equals(kind) ? xValues[index] : null,
                "unit", VisualContext.shortText(unit, 24), "values", inspected));
    }

    private void refreshDetail() {
        if (selected < 0) {
            if (!readOnly) detail.setText(card.local("点按图表查看数值", "Tap the chart to inspect values"));
            return;
        }
        StringBuilder text = new StringBuilder(labels[selected]);
        if ("scatter".equals(kind)) text.append("  ·  x ").append(number(xValues[selected], ""));
        for (int i = 0; i < names.length; i++) {
            text.append(i == 0 ? "  ·  " : "\n");
            if (names.length > 1) text.append(names[i]).append(' ');
            text.append(number(values[i][selected], unit));
        }
        if (radial && positiveTotal > 0 && Double.isFinite(values[0][selected])) {
            text.append("  ·  ").append(number(100 * Math.max(0, values[0][selected]) / positiveTotal, "%"));
        }
        detail.setText(text);
    }

    private float x(int index) {
        if (bars() || "heatmap".equals(kind)) return plot.left + plot.width() * (index + .5f) / labels.length;
        return plot.left + (float) ((xValues[index] - xMin) / (xMax - xMin) * plot.width());
    }
    private float y(double value) { return plot.bottom - (float) ((value - low) / (high - low) * plot.height()); }
    private double angle(int index) { return -Math.PI / 2 + Math.PI * 2 * index / labels.length; }
    private float slice(int index) {
        double value = values[0][index];
        return positiveTotal > 0 && Double.isFinite(value) ? (float) (360 * Math.max(0, value) / positiveTotal) : 0;
    }
    private boolean bars() { return "bar".equals(kind) || "stacked".equals(kind) || "histogram".equals(kind); }
    private int color(int index) { return COLORS[index % COLORS.length]; }

    private String number(double value, String suffix) {
        return Double.isFinite(value) ? card.display(value, 4, suffix) : "—";
    }

    private String axisNumber(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1e6 || (magnitude > 0 && magnitude < .001)) return String.format(Locale.ROOT, "%.1e", value);
        return number(value, "");
    }

    private String clip(String text, float width) {
        measure.setTextSize(paint.getTextSize());
        return TextUtils.ellipsize(text, measure, Math.max(1, width), TextUtils.TruncateAt.END).toString();
    }

    private void centered(Canvas canvas, String text, float x, float y, float width) {
        String trimmed = clip(text, width);
        canvas.drawText(trimmed, x - paint.measureText(trimmed) / 2, y, paint);
    }

    private static int blend(int start, int end, double amount) {
        return android.graphics.Color.rgb(channel(start, end, amount, 16), channel(start, end, amount, 8), channel(start, end, amount, 0));
    }

    private static int channel(int a, int b, double t, int shift) {
        return (int) Math.round(((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t);
    }

    private static String[] strings(JSONArray array) {
        String[] values = new String[array.length()];
        for (int i = 0; i < values.length; i++) values[i] = array.optString(i);
        return values;
    }

    private static Object highlightIndex(JSONObject item) {
        Object highlight = item.opt("highlight");
        if (highlight instanceof JSONArray && ((JSONArray) highlight).length() == 1) {
            return ((JSONArray) highlight).opt(0);
        }
        return highlight;
    }

    static void validate(VisualSpec spec, JSONObject item) throws JSONException {
        String kind = item.getString("kind");
        boolean breakdown = "pie".equals(kind) || "donut".equals(kind) || "funnel".equals(kind);
        boolean radar = "radar".equals(kind), heatmap = "heatmap".equals(kind), scatter = "scatter".equals(kind);
        VisualSpec.require(breakdown || radar || heatmap || scatter || "bar".equals(kind) || "line".equals(kind)
                || "stacked".equals(kind) || "area".equals(kind) || "histogram".equals(kind));
        JSONArray labels = VisualSpec.array(item.get("labels"), radar ? 3 : 2, breakdown ? 10 : radar ? 8 : heatmap ? 16 : 40);
        if (item.has("caption")) VisualSpec.string(item.get("caption"));
        if (item.has("highlight")) {
            double highlight = VisualSpec.number(highlightIndex(item));
            VisualSpec.require(highlight >= 0 && highlight < labels.length() && highlight == Math.rint(highlight));
        }
        for (int i = 0; i < labels.length(); i++) VisualSpec.require(VisualSpec.string(labels.get(i)).length() <= 40);
        if (scatter && item.has("x")) {
            JSONArray points = VisualSpec.array(item.get("x"), labels.length(), labels.length());
            for (int i = 0; i < points.length(); i++) VisualSpec.number(points.get(i));
        }
        JSONArray series = VisualSpec.array(item.get("series"), 1, breakdown || "histogram".equals(kind) ? 1 : heatmap ? 8 : 4);
        for (int i = 0; i < series.length(); i++) {
            JSONObject row = series.getJSONObject(i);
            VisualSpec.require(VisualSpec.string(row.get("label")).length() <= 50);
            JSONArray values = VisualSpec.array(row.get("values"), labels.length(), labels.length());
            for (int j = 0; j < values.length(); j++) if (!values.isNull(j)) spec.expression(values.get(j), 0);
        }
    }
}
