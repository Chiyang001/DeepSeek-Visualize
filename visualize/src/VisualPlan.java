package local.visualize;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualPlan {
    private VisualPlan() {}

    static View gantt(VisualCard card, JSONObject item) {
        boolean readOnly = card.isReadOnly();
        LinearLayout plan = card.section(card.chartSurface, card.border);
        plan.addView(card.text(item.optString("label"), 14, card.ink, true));
        JSONArray ticks = item.optJSONArray("ticks"), tasks = item.optJSONArray("tasks");
        LinearLayout scale = new LinearLayout(card.getContext());
        for (int i = 0; i < ticks.length(); i++) {
            TextView mark = card.text(ticks.optString(i), 11, card.secondary, false);
            mark.setGravity(Gravity.CENTER);
            scale.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.append(plan, scale, -2, 10);
        TextView detail = card.text(card.local("点按任务查看时间和详情", "Tap a task for dates and details"),
                13, card.secondary, false);
        View[] controls = new View[tasks.length()];
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            int start = task.optInt("start"), end = task.optInt("end");
            String name = task.optString("label");
            String span = ticks.optString(start) + " – " + ticks.optString(end - 1);
            TextView taskView = readOnly ? card.text(name + " · " + span, 13, card.ink, true) : card.button(name, false);
            controls[i] = taskView;
            taskView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            card.append(plan, taskView, -2, 8);
            View band = new View(card.getContext()) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    float width = getWidth() / (float) ticks.length();
                    paint.setColor(card.border);
                    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), card.dp(3), card.dp(3), paint);
                    paint.setColor(card.accent);
                    if (!readOnly && !taskView.isSelected()) paint.setAlpha(145);
                    canvas.drawRoundRect(start * width, 0, end * width, getHeight(), card.dp(3), card.dp(3), paint);
                }
            };
            band.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.append(plan, band, card.dp(7), 2);
            if (readOnly) {
                String description = task.optString("detail");
                if (!description.isEmpty()) card.append(plan, card.text(description, 12, card.secondary, false), -2, 4);
                continue;
            }
            taskView.setContentDescription(name + " · " + span);
            taskView.setOnClickListener(view -> {
                for (View control : controls) control.setSelected(control == taskView);
                for (int j = 0; j < plan.getChildCount(); j++) plan.getChildAt(j).invalidate();
                detail.setText(name + " · " + span + "\n" + task.optString("detail"));
                if (card.hasContext()) card.recordContext(item, "task", "schedule_selection", VisualContext.json(
                        "schedule", VisualCard.shortLabel(item), "task", VisualCard.shortLabel(task),
                        "starts", VisualContext.shortText(ticks.optString(start), 72),
                        "ends", VisualContext.shortText(ticks.optString(end - 1), 72)));
            });
        }
        if (!readOnly) card.append(plan, detail, -2, 10);
        return plan;
    }

    static View diagram(VisualCard card, JSONObject item) {
        LinearLayout outer = card.section(card.chartSurface, card.border);
        outer.addView(card.text(item.optString("label"), 14, card.ink, true));
        TextView detail = card.text(card.local("点按节点查看说明", "Tap a node to see details"), 13, card.secondary, false);
        card.append(outer, new Diagram(card, item, detail), -2, 10);
        if (!card.isReadOnly()) card.append(outer, detail, -2, 10);
        return outer;
    }

    private static final class Diagram extends LinearLayout {
        private final VisualCard card;
        private final JSONArray edges;
        private final Map<String, View> nodeViews = new HashMap<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String selected;

        Diagram(VisualCard card, JSONObject item, TextView detail) {
            super(card.getContext());
            this.card = card;
            boolean readOnly = card.isReadOnly();
            edges = item.optJSONArray("edges");
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            ArrayList<ArrayList<JSONObject>> levels = new ArrayList<>();
            for (int i = 0; i < 6; i++) levels.add(new ArrayList<>());
            JSONArray nodes = item.optJSONArray("nodes");
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                levels.get(node.optInt("layer")).add(node);
            }
            for (ArrayList<JSONObject> level : levels) {
                if (level.isEmpty()) continue;
                LinearLayout row = new LinearLayout(getContext());
                row.setGravity(Gravity.CENTER);
                for (JSONObject node : level) {
                    String id = node.optString("id"), name = node.optString("label");
                    View nodeView;
                    if (readOnly) {
                        LinearLayout tile = card.section(card.panel, card.border);
                        tile.addView(card.text(name, 13, card.ink, true));
                        String description = node.optString("detail");
                        if (!description.isEmpty()) {
                            card.append(tile, card.text(description, 12, card.secondary, false), -2, 5);
                        }
                        nodeView = tile;
                    } else nodeView = card.button(name, false);
                    nodeViews.put(id, nodeView);
                    LayoutParams params = new LayoutParams(0, -2, 1);
                    params.setMargins(card.dp(3), 0, card.dp(3), 0);
                    row.addView(nodeView, params);
                    if (!readOnly) nodeView.setOnClickListener(view -> {
                        selected = id;
                        for (Map.Entry<String, View> entry : nodeViews.entrySet()) {
                            boolean active = id.equals(entry.getKey());
                            entry.getValue().setSelected(active);
                            card.styleButton((Button) entry.getValue(), active);
                        }
                        detail.setText(name + "\n" + node.optString("detail"));
                        invalidate();
                        if (card.hasContext()) card.recordContext(item, "node", "diagram_selection", VisualContext.json(
                                "diagram", VisualCard.shortLabel(item), "node", VisualCard.shortLabel(node), "node_id", id));
                    });
                }
                card.append(this, row, -2, getChildCount() == 0 ? 0 : 20);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStrokeWidth(card.dp(1.5f));
            for (int i = 0; i < edges.length(); i++) {
                JSONArray edge = edges.optJSONArray(i);
                String from = edge.optString(0), to = edge.optString(1);
                View a = nodeViews.get(from), b = nodeViews.get(to);
                View rowA = (View) a.getParent(), rowB = (View) b.getParent();
                float ax = rowA.getLeft() + a.getLeft() + a.getWidth() / 2f;
                float bx = rowB.getLeft() + b.getLeft() + b.getWidth() / 2f;
                float ay = rowA.getTop() + a.getBottom(), by = rowB.getTop() + b.getTop();
                float mid = (ay + by) / 2;
                paint.setColor(from.equals(selected) || to.equals(selected) ? card.accent : card.secondary);
                paint.setStyle(Paint.Style.STROKE);
                Path line = new Path();
                line.moveTo(ax, ay); line.lineTo(ax, mid); line.lineTo(bx, mid); line.lineTo(bx, by);
                canvas.drawPath(line, paint);
                paint.setStyle(Paint.Style.FILL);
                Path arrow = new Path();
                arrow.moveTo(bx, by); arrow.lineTo(bx - card.dp(3), by - card.dp(5));
                arrow.lineTo(bx + card.dp(3), by - card.dp(5)); arrow.close();
                canvas.drawPath(arrow, paint);
            }
        }
    }

    static void validateGantt(JSONObject item) throws JSONException {
        JSONArray ticks = VisualSpec.array(item.get("ticks"), 2, 8);
        for (int i = 0; i < ticks.length(); i++) VisualSpec.string(ticks.get(i));
        JSONArray tasks = VisualSpec.array(item.get("tasks"), 1, 12);
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            VisualSpec.string(task.get("label"));
            VisualSpec.string(task.get("detail"));
            int start = integer(task.get("start"), 0, ticks.length() - 1);
            integer(task.get("end"), start + 1, ticks.length());
        }
    }

    static void validateDiagram(JSONObject item) throws JSONException {
        JSONArray nodes = VisualSpec.array(item.get("nodes"), 2, 12);
        HashMap<String, Integer> layers = new HashMap<>();
        int[] widths = new int[6];
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String id = VisualSpec.string(node.get("id"));
            VisualSpec.string(node.get("label"));
            VisualSpec.string(node.get("detail"));
            int level = integer(node.get("layer"), 0, 5);
            VisualSpec.require(!id.isEmpty() && layers.put(id, level) == null && ++widths[level] <= 3);
        }
        JSONArray edges = VisualSpec.array(item.get("edges"), 1, 18);
        for (int i = 0; i < edges.length(); i++) {
            JSONArray edge = VisualSpec.array(edges.get(i), 2, 2);
            Integer source = layers.get(VisualSpec.string(edge.get(0))), target = layers.get(VisualSpec.string(edge.get(1)));
            VisualSpec.require(source != null && target != null && source < target);
        }
    }

    private static int integer(Object value, int min, int max) {
        double number = VisualSpec.number(value);
        VisualSpec.require(number == Math.rint(number) && number >= min && number <= max);
        return (int) number;
    }
}
