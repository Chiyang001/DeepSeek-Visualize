package local.visualize;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.DataInputStream;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualMap extends View {
    private static Path land;
    private final VisualCard card;
    private final JSONObject component;
    private final JSONArray markers;
    private final double[] latitudes, longitudes;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline;
    private final TextView detail;
    private final boolean route;
    private final double fitLon, fitLat, fitSpanLon, fitSpanLat;
    private double centerLon, centerLat, zoom = 1;
    private int selected = -1;
    private float downX, downY, scale;

    static View create(VisualCard card, JSONObject item) {
        LinearLayout panel = card.section(card.chartSurface, card.border);
        panel.addView(card.text(item.optString("label"), 14, card.ink, true));
        TextView detail = card.text(card.local("点按标记或地点查看详情", "Tap a marker or place for details"),
                13, card.secondary, false);
        VisualMap map = new VisualMap(card, item, detail);
        card.append(panel, map, card.dp(196), 7);

        if (card.isReadOnly()) {
            for (int i = 0; i < map.markers.length(); i++) {
                JSONObject marker = map.markers.optJSONObject(i);
                String description = marker.optString("detail");
                String label = (i + 1) + "  ·  " + marker.optString("label");
                card.append(panel, card.text(label, 13, card.ink, true), -2, 7);
                if (!description.isEmpty()) card.append(panel, card.text(description, 12, card.secondary, false), -2, 3);
            }
            appendCaption(card, panel, map.route);
            return panel;
        }

        LinearLayout controls = new LinearLayout(card.getContext());
        Button fit = card.button(card.local("适合视图", "Fit view"), false);
        Button minus = card.button("−", false), plus = card.button("+", false);
        minus.setContentDescription(card.local("缩小地图", "Zoom out"));
        plus.setContentDescription(card.local("放大地图", "Zoom in"));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(minus, new LinearLayout.LayoutParams(card.dp(49), -2));
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -2, 1);
        middle.setMargins(card.dp(6), 0, card.dp(6), 0);
        controls.addView(fit, middle);
        controls.addView(plus, new LinearLayout.LayoutParams(card.dp(49), -2));
        card.append(panel, controls, -2, 6);
        fit.setOnClickListener(view -> map.fit());
        minus.setOnClickListener(view -> map.zoomBy(.5));
        plus.setOnClickListener(view -> map.zoomBy(2));

        HorizontalScrollView scroll = new HorizontalScrollView(card.getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout places = new LinearLayout(card.getContext());
        scroll.addView(places);
        for (int i = 0; i < map.markers.length(); i++) {
            final int index = i;
            Button place = card.button(map.markers.optJSONObject(i).optString("label"), false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginEnd(card.dp(5));
            places.addView(place, params);
            place.setOnClickListener(view -> map.choose(index));
        }
        card.append(panel, scroll, -2, 6);
        card.append(panel, detail, -2, 8);
        appendCaption(card, panel, map.route);
        return panel;
    }

    private static void appendCaption(VisualCard card, LinearLayout panel, boolean route) {
        String caption = route ? card.local("示意连线 · Natural Earth", "Schematic route · Natural Earth") : "Natural Earth";
        card.append(panel, card.text(caption, 11, card.secondary, false), -2, 5);
    }

    private VisualMap(VisualCard card, JSONObject item, TextView detail) {
        super(card.getContext());
        this.card = card;
        component = item;
        this.detail = detail;
        markers = item.optJSONArray("markers");
        latitudes = new double[markers.length()];
        longitudes = new double[markers.length()];
        double west = 180, east = -180, south = 90, north = -90;
        StringBuilder summary = new StringBuilder(item.optString("label"));
        for (int i = 0; i < markers.length(); i++) {
            JSONObject marker = markers.optJSONObject(i);
            double lat = marker.optDouble("lat"), lon = marker.optDouble("lon");
            latitudes[i] = lat;
            longitudes[i] = lon;
            west = Math.min(west, lon); east = Math.max(east, lon);
            south = Math.min(south, lat); north = Math.max(north, lat);
            summary.append(" · ").append(marker.optString("label"));
        }
        fitLon = (west + east) / 2;
        fitLat = (south + north) / 2;
        fitSpanLon = Math.min(360, Math.max(18, (east - west) * 1.35));
        fitSpanLat = Math.min(180, Math.max(12, (north - south) * 1.35));
        route = item.optBoolean("route");
        outline = outline(getContext());
        setBackground(card.shape(card.panel, 13, card.border));
        setClipToOutline(true);
        setContentDescription(summary);
        setClickable(!card.isReadOnly());
        fit();
    }

    private static synchronized Path outline(Context context) {
        if (land != null) return land;
        // Public-domain Natural Earth 1:110m land polygons, packed at 0.01 degree precision.
        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        try (DataInputStream input = new DataInputStream(context.getAssets().open("visualize-world-land.bin"))) {
            int rings = input.readUnsignedShort();
            for (int i = 0; i < rings; i++) {
                int points = input.readUnsignedShort();
                for (int j = 0; j < points; j++) {
                    float x = input.readShort() / 100f, y = -input.readShort() / 100f;
                    if (j == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
            }
        } catch (IOException invalid) { path.reset(); }
        land = path;
        return land;
    }

    private void fit() { zoom = 1; centerLon = fitLon; centerLat = fitLat; invalidate(); }

    private void zoomBy(double factor) {
        zoom = Math.max(.25, Math.min(4, zoom * factor));
        if (selected >= 0) { centerLon = longitudes[selected]; centerLat = latitudes[selected]; }
        invalidate();
    }

    private void choose(int index) {
        selected = index;
        JSONObject marker = markers.optJSONObject(index);
        String description = marker.optString("detail");
        detail.setText(marker.optString("label") + " · " + card.display(latitudes[index], 2, "°")
                + ", " + card.display(longitudes[index], 2, "°") + (description.isEmpty() ? "" : "\n" + description));
        if (x(longitudes[index]) < 0 || x(longitudes[index]) > getWidth()
                || y(latitudes[index]) < 0 || y(latitudes[index]) > getHeight()) {
            centerLon = longitudes[index]; centerLat = latitudes[index];
        }
        invalidate();
        if (card.hasContext()) card.recordContext(component, "marker", "map_selection", VisualContext.json(
                "map", VisualCard.shortLabel(component), "place", VisualCard.shortLabel(marker),
                "latitude", latitudes[index], "longitude", longitudes[index]));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        scale = (float) (Math.min((getWidth() - card.dp(20)) / fitSpanLon,
                (getHeight() - card.dp(20)) / fitSpanLat) * zoom);
        if (scale <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(card.border);
        canvas.save();
        canvas.translate(x(0), y(0));
        canvas.scale(scale, scale);
        canvas.drawPath(outline, paint);
        canvas.restore();
        paint.setColor(card.secondary);
        paint.setAlpha(50);
        paint.setStrokeWidth(card.dp(1));
        int interval = scale > card.dp(6) ? 5 : scale > card.dp(2) ? 10 : 30;
        for (int lon = -180; lon <= 180; lon += interval) canvas.drawLine(x(lon), y(-90), x(lon), y(90), paint);
        for (int lat = -90; lat <= 90; lat += interval) canvas.drawLine(x(-180), y(lat), x(180), y(lat), paint);
        paint.setAlpha(255);
        if (route) {
            paint.setColor(card.accent);
            paint.setStrokeWidth(card.dp(2));
            for (int i = 1; i < markers.length(); i++) drawRoute(canvas, i - 1, i);
        }
        for (int i = 0; i < markers.length(); i++) {
            float x = x(longitudes[i]), y = y(latitudes[i]);
            boolean readOnly = card.isReadOnly();
            paint.setColor(card.chartSurface);
            canvas.drawCircle(x, y, card.dp(readOnly || i == selected ? 9 : 7), paint);
            paint.setColor(card.accent);
            canvas.drawCircle(x, y, card.dp(readOnly || i == selected ? 7 : 5), paint);
            if (readOnly) {
                paint.setColor(card.surface);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(card.dp(9));
                canvas.drawText(Integer.toString(i + 1), x, y + card.dp(3), paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    private void drawRoute(Canvas canvas, int a, int b) {
        double first = longitudes[a], last = longitudes[b], difference = last - first;
        if (Math.abs(difference) <= 180) {
            canvas.drawLine(x(first), y(latitudes[a]), x(last), y(latitudes[b]), paint);
            return;
        }
        double edge = difference > 0 ? -180 : 180;
        double adjusted = last + edge * 2;
        if (adjusted == first) {
            canvas.drawLine(x(first), y(latitudes[a]), x(first), y(latitudes[b]), paint);
            return;
        }
        double latitude = latitudes[a] + (latitudes[b] - latitudes[a]) * (edge - first) / (adjusted - first);
        canvas.drawLine(x(first), y(latitudes[a]), x(edge), y(latitude), paint);
        canvas.drawLine(x(-edge), y(latitude), x(last), y(latitudes[b]), paint);
    }

    private float x(double lon) { return getWidth() / 2f + (float) (lon - centerLon) * scale; }
    private float y(double lat) { return getHeight() / 2f - (float) (lat - centerLat) * scale; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (card.isReadOnly()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                && Math.hypot(event.getX() - downX, event.getY() - downY) <= ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            int nearest = -1;
            double best = card.dp(28);
            for (int i = 0; i < markers.length(); i++) {
                double distance = Math.hypot(event.getX() - x(longitudes[i]), event.getY() - y(latitudes[i]));
                if (distance < best) { best = distance; nearest = i; }
            }
            if (nearest >= 0) { choose(nearest); performClick(); }
        }
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }

    static void validate(JSONObject item) throws JSONException {
        JSONArray markers = VisualSpec.array(item.get("markers"), 1, 16);
        if (item.has("route")) VisualSpec.require(item.get("route") instanceof Boolean);
        for (int i = 0; i < markers.length(); i++) {
            JSONObject marker = markers.getJSONObject(i);
            VisualSpec.string(marker.get("label"));
            if (marker.has("detail")) VisualSpec.string(marker.get("detail"));
            VisualSpec.require(Math.abs(VisualSpec.number(marker.get("lat"))) <= 90);
            VisualSpec.require(Math.abs(VisualSpec.number(marker.get("lon"))) <= 180);
        }
    }
}
