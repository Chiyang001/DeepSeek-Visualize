package local.visualize;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualSections {
    private VisualSections() {}

    static View group(VisualCard card, JSONObject item) {
        LinearLayout outer = column(card);
        String title = item.optString("label");
        if (!title.isEmpty()) outer.addView(card.text(title, 15, card.ink, true));
        JSONArray pages = item.optJSONArray("pages");
        boolean tabs = "tabs".equals(item.optString("mode"));
        LinearLayout content = column(card);
        LinearLayout strip = new LinearLayout(card.getContext());
        if (tabs) {
            HorizontalScrollView scroll = new HorizontalScrollView(card.getContext());
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(strip);
            card.append(outer, scroll, LinearLayout.LayoutParams.WRAP_CONTENT, 7);
        }
        card.append(outer, content, LinearLayout.LayoutParams.WRAP_CONTENT, 0);
        LinearLayout[] panels = new LinearLayout[pages.length()];
        Button[] switches = new Button[pages.length()];
        for (int i = 0; i < pages.length(); i++) {
            final int index = i;
            JSONObject page = pages.optJSONObject(i);
            String label = page.optString("label");
            LinearLayout panel = card.section(card.panel, 0);
            panels[i] = panel;
            card.renderItems(panel, page.optJSONArray("items"));
            if (tabs) {
                Button tab = card.button(label, i == 0);
                tab.setSelected(i == 0);
                switches[i] = tab;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.setMarginEnd(card.dp(7));
                strip.addView(tab, params);
                panel.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
                tab.setOnClickListener(view -> {
                    for (int j = 0; j < panels.length; j++) {
                        boolean active = j == index;
                        panels[j].setVisibility(active ? View.VISIBLE : View.GONE);
                        switches[j].setSelected(active);
                        card.styleButton(switches[j], active);
                    }
                });
            } else addDisclosure(card, content, label, panel);
            card.append(content, panel, LinearLayout.LayoutParams.WRAP_CONTENT, 6);
        }
        return outer;
    }

    static View deck(VisualCard card, JSONObject item) {
        LinearLayout outer = card.section(card.panel, 0);
        String kind = item.optString("kind");
        outer.addView(card.text(item.optString("label"), 15, card.ink, true));
        JSONArray entries = item.optJSONArray("entries");
        if ("flashcards".equals(kind)) {
            card.append(outer, new Flashcards(card, item, entries), LinearLayout.LayoutParams.WRAP_CONTENT, 10);
        } else if ("steps".equals(kind)) {
            addSteps(card, outer, item, entries);
        } else {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                TextView detail = card.text(entry.optString("detail"), 14, card.secondary, false);
                detail.setPadding(card.dp(14), card.dp(5), card.dp(8), card.dp(5));
                String tag = entry.optString("tag");
                String heading = tag.isEmpty() ? entry.optString("label") : tag + " · " + entry.optString("label");
                addDisclosure(card, outer, heading, detail);
                card.append(outer, detail, LinearLayout.LayoutParams.WRAP_CONTENT, 3);
            }
        }
        return outer;
    }

    private static void addSteps(VisualCard card, LinearLayout outer, JSONObject item, JSONArray entries) {
        TextView status = card.text("", 12, card.secondary, true);
        card.append(outer, status, LinearLayout.LayoutParams.WRAP_CONTENT, 7);
        ProgressBar progress = new ProgressBar(card.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressDrawable(card.track(card.border, card.accent, 5));
        progress.setMax(entries.length());
        card.append(outer, progress, card.dp(5), 6);
        CheckBox[] steps = new CheckBox[entries.length()];
        Runnable update = () -> {
            int count = 0;
            for (CheckBox step : steps) if (step != null && step.isChecked()) count++;
            status.setText(count + "/" + entries.length() + card.local(" 已完成", " complete"));
            progress.setProgress(count);
        };
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            CheckBox step = new CheckBox(card.getContext());
            step.setMinHeight(card.dp(44));
            step.setTextColor(card.ink);
            step.setText(entry.optString("label"));
            step.setButtonTintList(android.content.res.ColorStateList.valueOf(card.accent));
            steps[i] = step;
            step.setOnCheckedChangeListener((view, checked) -> {
                update.run();
                if (!card.hasContext()) return;
                JSONArray completed = new JSONArray();
                for (CheckBox option : steps) {
                    if (option != null && option.isChecked()) {
                        completed.put(VisualContext.shortText(option.getText().toString(), 72));
                    }
                }
                card.recordContext(item, "steps", "steps_progress", VisualContext.json(
                        "checklist", VisualCard.shortLabel(item), "completed_steps", completed, "total_steps", entries.length()));
            });
            card.append(outer, step, LinearLayout.LayoutParams.WRAP_CONTENT, 4);
            TextView detail = card.text(entry.optString("detail"), 13, card.secondary, false);
            detail.setPadding(card.dp(12), 0, 0, card.dp(4));
            card.append(outer, detail, LinearLayout.LayoutParams.WRAP_CONTENT, 0);
        }
        update.run();
    }

    private static void addDisclosure(VisualCard card, LinearLayout container, String heading, View detail) {
        Button control = card.button(heading + "   ▾", false);
        control.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        detail.setVisibility(View.GONE);
        control.setOnClickListener(view -> {
            boolean show = detail.getVisibility() != View.VISIBLE;
            detail.setVisibility(show ? View.VISIBLE : View.GONE);
            control.setText(heading + (show ? "   ▴" : "   ▾"));
        });
        card.append(container, control, LinearLayout.LayoutParams.WRAP_CONTENT, 8);
    }

    private static LinearLayout column(VisualCard card) {
        LinearLayout group = new LinearLayout(card.getContext());
        group.setOrientation(LinearLayout.VERTICAL);
        return group;
    }

    private static final class Flashcards extends LinearLayout {
        private final VisualCard card;
        private final JSONArray entries;
        private final TextView content, position;
        private final Button flip, previous, next;
        private int current;
        private boolean showingAnswer;

        Flashcards(VisualCard card, JSONObject item, JSONArray entries) {
            super(card.getContext());
            this.card = card;
            this.entries = entries;
            setOrientation(VERTICAL);
            content = card.text("", 18, card.ink, true);
            content.setGravity(Gravity.CENTER);
            content.setMinHeight(card.dp(78));
            addView(content);
            flip = card.button("", true);
            card.append(this, flip, LayoutParams.WRAP_CONTENT, 8);
            flip.setOnClickListener(view -> {
                showingAnswer = !showingAnswer;
                render();
                if (card.hasContext() && showingAnswer) {
                    card.recordContext(item, "review", "flashcard_review", VisualContext.json("deck", VisualCard.shortLabel(item),
                            "card_number", current + 1, "prompt", VisualCard.shortLabel(entries.optJSONObject(current))));
                }
            });

            LinearLayout navigation = new LinearLayout(getContext());
            navigation.setGravity(Gravity.CENTER_VERTICAL);
            previous = card.button("‹", false);
            previous.setContentDescription(card.local("上一张", "Previous card"));
            next = card.button("›", false);
            next.setContentDescription(card.local("下一张", "Next card"));
            position = card.text("", 13, card.secondary, false);
            position.setGravity(Gravity.CENTER);
            navigation.addView(previous, new LayoutParams(card.dp(49), LayoutParams.WRAP_CONTENT));
            navigation.addView(position, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            navigation.addView(next, new LayoutParams(card.dp(49), LayoutParams.WRAP_CONTENT));
            card.append(this, navigation, LayoutParams.WRAP_CONTENT, 8);
            previous.setOnClickListener(view -> { current--; showingAnswer = false; render(); });
            next.setOnClickListener(view -> { current++; showingAnswer = false; render(); });
            render();
        }

        private void render() {
            content.setText(entries.optJSONObject(current).optString(showingAnswer ? "detail" : "label"));
            flip.setText(showingAnswer ? card.local("查看题面", "Show prompt") : card.local("翻面看答案", "Show answer"));
            position.setText((current + 1) + " / " + entries.length());
            previous.setEnabled(current > 0);
            next.setEnabled(current < entries.length() - 1);
        }
    }

    static void validateDeck(JSONObject item) throws JSONException {
        String kind = item.getString("kind");
        VisualSpec.require("flashcards".equals(kind) || "timeline".equals(kind) || "steps".equals(kind));
        JSONArray entries = VisualSpec.array(item.get("entries"), 1, 12);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            VisualSpec.string(entry.get("label"));
            VisualSpec.string(entry.get("detail"));
            if (entry.has("tag")) VisualSpec.string(entry.get("tag"));
        }
    }
}
