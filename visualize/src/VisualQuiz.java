package local.visualize;

import android.content.res.ColorStateList;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VisualQuiz extends LinearLayout {
    private final VisualCard card;
    private final JSONObject component;
    private final boolean chinese;
    private final Question[] questions;
    private final TextView progressLabel, score;
    private final ProgressBar progress;
    private final LinearLayout result;
    private final Button submit;
    private boolean submitted;

    VisualQuiz(VisualCard card, JSONObject component) {
        super(card.getContext());
        this.card = card;
        this.component = component;
        JSONArray items = component.optJSONArray("questions");
        chinese = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
        questions = new Question[items.length()];
        setOrientation(VERTICAL);

        TextView count = card.text(local("共 " + items.length() + " 题", items.length() + " questions"),
                12, card.secondary, false);
        progressLabel = card.text("", 12, card.accent, true);
        progressLabel.setPadding(card.dp(9), card.dp(3), card.dp(9), card.dp(3));
        progressLabel.setBackground(card.shape(card.panel, 30, 0));
        LinearLayout statusRow = new LinearLayout(getContext());
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(count, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        statusRow.addView(progressLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(statusRow);
        progress = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(questions.length);
        progress.setProgressDrawable(card.track(card.border, card.accent, 4));
        progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.append(this, progress, card.dp(4), 10);

        for (int i = 0; i < questions.length; i++) {
            if (i != 0) {
                View separator = new View(getContext());
                separator.setBackgroundColor(card.border);
                card.append(this, separator, card.dp(1), 14);
            }
            questions[i] = new Question(items.optJSONObject(i), i);
        }

        result = card.section(card.panel, 0);
        TextView scoreLabel = card.text(local("最终得分", "Final score"), 13, card.secondary, false);
        scoreLabel.setGravity(Gravity.CENTER);
        result.addView(scoreLabel);
        score = card.text("", 29, card.ink, true);
        score.setGravity(Gravity.CENTER);
        card.append(result, score, LayoutParams.WRAP_CONTENT, 3);
        result.setVisibility(GONE);
        card.append(this, result, LayoutParams.WRAP_CONTENT, 12);

        submit = card.button(local("提交答案", "Submit answers"), true);
        card.append(this, submit, LayoutParams.WRAP_CONTENT, 12);
        submit.setOnClickListener(view -> {
            if (submitted) reset();
            else submit();
        });
        updateProgress();
    }

    private String local(String zh, String en) { return chinese ? zh : en; }

    private void updateProgress() {
        int count = 0;
        for (Question question : questions) if (question != null && question.answered()) count++;
        progress.setProgress(count);
        progressLabel.setText(count + "/" + questions.length + local(" 已作答", " answered"));
        submit.setEnabled(submitted || count == questions.length);
    }

    private void submit() {
        View focused = findFocus();
        if (focused != null) focused.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
        submitted = true;
        int correct = 0;
        JSONArray results = new JSONArray();
        for (int i = 0; i < questions.length; i++) {
            Question question = questions[i];
            boolean passed = question.showResult();
            if (passed) correct++;
            if (card.hasContext()) results.put(question.context(i + 1, passed));
        }
        int percent = Math.round(correct * 100f / questions.length);
        score.setText(percent + " / 100");
        result.setVisibility(VISIBLE);
        submit.setText(local("重新答题", "Try again"));
        if (card.hasContext()) card.recordContext(component, "submitted", "quiz_result", VisualContext.json(
                "score", percent, "score_max", 100, "correct_count", correct, "question_count", questions.length,
                "questions", results));
    }

    private void reset() {
        submitted = false;
        for (Question question : questions) question.reset();
        result.setVisibility(GONE);
        submit.setText(local("提交答案", "Submit answers"));
        updateProgress();
    }

    private final class Question {
        private final String mode;
        private final JSONObject item;
        private final ArrayList<Integer> selected = new ArrayList<>();
        private final ArrayList<VisualField> fields = new ArrayList<>();
        private final LinearLayout answers;
        private final LinearLayout feedback;
        private final TextView status;

        Question(JSONObject item, int index) {
            this.item = item;
            mode = item.optString("mode", "single");
            LinearLayout header = new LinearLayout(getContext());
            TextView badge = card.text(Integer.toString(index + 1), 12, card.ink, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(card.shape(card.panel, 20, 0));
            LayoutParams badgeParams = new LayoutParams(card.dp(23), card.dp(23));
            badgeParams.setMarginEnd(card.dp(8));
            header.addView(badge, badgeParams);
            TextView question = card.text(item.optString("question"), 15, card.ink, true);
            header.addView(question, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            card.append(VisualQuiz.this, header, LayoutParams.WRAP_CONTENT, 13);

            answers = "single".equals(mode) ? new RadioGroup(getContext()) : new LinearLayout(getContext());
            answers.setOrientation(VERTICAL);
            if ("fill".equals(mode)) addFields();
            else addOptions();
            card.append(VisualQuiz.this, answers, LayoutParams.WRAP_CONTENT, 5);

            feedback = card.section(card.panel, 0);
            status = card.text("", 13, card.ink, true);
            feedback.addView(status);
            card.append(feedback, card.text(item.optString("explanation"), 13, card.secondary, false),
                    LayoutParams.WRAP_CONTENT, 4);
            feedback.setVisibility(GONE);
            card.append(VisualQuiz.this, feedback, LayoutParams.WRAP_CONTENT, 6);
        }

        private void addFields() {
            int blanks = item.optJSONArray("blanks").length();
            for (int i = 0; i < blanks; i++) {
                String hint = blanks == 1 ? local("输入答案", "Enter answer")
                        : local("第 " + (i + 1) + " 空", "Blank " + (i + 1));
                VisualField field = new VisualField(card, hint, InputType.TYPE_CLASS_TEXT);
                field.onText(text -> updateProgress());
                fields.add(field);
                card.append(answers, field, LayoutParams.WRAP_CONTENT, 6);
            }
        }

        private void addOptions() {
            JSONArray options = item.optJSONArray("options");
            if ("order".equals(mode)) {
                for (int i = 0; i < options.length(); i++) {
                    final int index = i;
                    Button option = card.button(options.optString(i), false);
                    option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    option.setOnClickListener(view -> {
                        if (!selected.remove(Integer.valueOf(index))) selected.add(index);
                        updateOrder();
                        updateProgress();
                    });
                    card.append(answers, option, LayoutParams.WRAP_CONTENT, 5);
                }
                updateOrder();
                return;
            }
            boolean multiple = "multi".equals(mode);
            for (int i = 0; i < options.length(); i++) {
                CompoundButton option = multiple ? new CheckBox(getContext()) : new RadioButton(getContext());
                option.setId(View.generateViewId());
                option.setText(optionLetter(i) + ". " + options.optString(i));
                option.setTextSize(14);
                option.setTextColor(card.ink);
                option.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                        new int[]{card.ink, card.secondary}));
                option.setMinHeight(card.dp(44));
                option.setPadding(card.dp(2), card.dp(4), card.dp(6), card.dp(4));
                answers.addView(option, multiple ? new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                        : new RadioGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                if (multiple) {
                    final int index = i;
                    option.setOnCheckedChangeListener((view, checked) -> {
                        if (checked) selected.add(index);
                        else selected.remove(Integer.valueOf(index));
                        updateProgress();
                    });
                }
            }
            if (multiple) return;
            ((RadioGroup) answers).setOnCheckedChangeListener((radio, checked) -> {
                selected.clear();
                for (int i = 0; i < radio.getChildCount(); i++) {
                    if (radio.getChildAt(i).getId() == checked) {
                        selected.add(i);
                        break;
                    }
                }
                updateProgress();
            });
        }

        private void updateOrder() {
            JSONArray options = item.optJSONArray("options");
            for (int i = 0; i < options.length(); i++) {
                int rank = selected.indexOf(i);
                ((Button) answers.getChildAt(i)).setText((rank < 0 ? "○" : Integer.toString(rank + 1))
                        + "  " + options.optString(i));
            }
        }

        boolean answered() {
            if ("order".equals(mode)) return selected.size() == answers.getChildCount();
            if (!"fill".equals(mode)) return !selected.isEmpty();
            for (VisualField field : fields) if (normalize(field.getText().toString()).isEmpty()) return false;
            return true;
        }

        JSONObject context(int number, boolean correct) {
            JSONArray actual = new JSONArray(), expected = new JSONArray();
            if ("fill".equals(mode)) {
                JSONArray blanks = item.optJSONArray("blanks");
                for (int i = 0; i < fields.size(); i++) {
                    actual.put(VisualContext.shortText(fields.get(i).getText().toString().trim(), 56));
                    expected.put(VisualContext.shortText(blanks.optJSONArray(i).optString(0), 56));
                }
            } else {
                for (int index : selected) actual.put(answerLabel(index));
                if ("single".equals(mode)) expected.put(answerLabel(item.optInt("answer")));
                else {
                    JSONArray keys = item.optJSONArray("answers");
                    for (int i = 0; i < keys.length(); i++) expected.put(answerLabel(keys.optInt(i)));
                }
            }
            return VisualContext.json("question_number", number,
                    "question", VisualContext.shortText(item.optString("question"), 130), "mode", mode,
                    "user_answers", actual, "reference_answers", expected, "correct", correct);
        }

        private String answerLabel(int index) {
            return optionLetter(index) + ". " + VisualContext.shortText(item.optJSONArray("options").optString(index), 40);
        }

        boolean showResult() {
            boolean correct = true;
            ArrayList<String> expected = new ArrayList<>();
            if ("fill".equals(mode)) {
                JSONArray blanks = item.optJSONArray("blanks");
                for (int i = 0; i < fields.size(); i++) {
                    JSONArray aliases = blanks.optJSONArray(i);
                    String typed = normalize(fields.get(i).getText().toString());
                    boolean matches = false;
                    for (int j = 0; j < aliases.length(); j++) matches |= typed.equals(normalize(aliases.optString(j)));
                    correct &= matches;
                    expected.add(aliases.optString(0));
                }
            } else if ("single".equals(mode)) {
                int answer = item.optInt("answer");
                correct = selected.size() == 1 && selected.get(0) == answer;
                expected.add(Character.toString(optionLetter(answer)));
            } else {
                JSONArray keys = item.optJSONArray("answers");
                correct = selected.size() == keys.length();
                for (int i = 0; i < keys.length(); i++) {
                    int answer = keys.optInt(i);
                    correct &= "multi".equals(mode) ? selected.contains(answer)
                            : i < selected.size() && selected.get(i) == answer;
                    expected.add(Character.toString(optionLetter(answer)));
                }
            }
            status.setText(correct ? local("✓ 回答正确", "✓ Correct") : local("× 参考答案 ", "× Answer: ")
                    + String.join("order".equals(mode) ? " → " : " / ", expected));
            status.setTextColor(correct ? card.success : card.error);
            feedback.setVisibility(VISIBLE);
            setOptionsEnabled(false);
            return correct;
        }

        void reset() {
            selected.clear();
            for (int i = 0; i < answers.getChildCount(); i++) {
                View child = answers.getChildAt(i);
                if (child instanceof VisualField) ((VisualField) child).setText("");
                else if (child instanceof CompoundButton) ((CompoundButton) child).setChecked(false);
            }
            if (answers instanceof RadioGroup) ((RadioGroup) answers).clearCheck();
            if ("order".equals(mode)) updateOrder();
            setOptionsEnabled(true);
            feedback.setVisibility(GONE);
        }

        private void setOptionsEnabled(boolean enabled) {
            for (int i = 0; i < answers.getChildCount(); i++) answers.getChildAt(i).setEnabled(enabled);
        }

        private char optionLetter(int index) { return (char) ('A' + index); }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static void validate(JSONObject item) throws JSONException {
        JSONArray questions = VisualSpec.array(item.get("questions"), 1, 8);
        for (int i = 0; i < questions.length(); i++) {
            JSONObject question = questions.getJSONObject(i);
            VisualSpec.string(question.get("question"));
            VisualSpec.string(question.get("explanation"));
            String mode = question.optString("mode", "single");
            if ("fill".equals(mode)) {
                JSONArray blanks = VisualSpec.array(question.get("blanks"), 1, 4);
                for (int j = 0; j < blanks.length(); j++) {
                    JSONArray aliases = VisualSpec.array(blanks.get(j), 1, 6);
                    for (int k = 0; k < aliases.length(); k++) {
                        VisualSpec.require(!normalize(VisualSpec.string(aliases.get(k))).isEmpty());
                    }
                }
                continue;
            }
            JSONArray options = VisualSpec.array(question.get("options"), 2, 6);
            for (int j = 0; j < options.length(); j++) VisualSpec.string(options.get(j));
            if ("single".equals(mode)) {
                index(question.get("answer"), options.length());
            } else {
                boolean order = "order".equals(mode);
                VisualSpec.require(order || "multi".equals(mode));
                JSONArray keys = VisualSpec.array(question.get("answers"), order ? options.length() : 1, options.length());
                boolean[] used = new boolean[options.length()];
                for (int j = 0; j < keys.length(); j++) {
                    int key = index(keys.get(j), options.length());
                    VisualSpec.require(!used[key]);
                    used[key] = true;
                }
            }
        }
    }

    private static int index(Object value, int size) {
        double number = VisualSpec.number(value);
        VisualSpec.require(number >= 0 && number < size && number == Math.rint(number));
        return (int) number;
    }
}
