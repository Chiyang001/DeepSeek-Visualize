package local.visualize;

import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import java.util.function.Consumer;

final class VisualField extends EditText {
    private final Runnable revealAfterKeyboard = () -> {
        if (hasFocus() && isAttachedToWindow()) requestRectangleOnScreen(new Rect(0, 0, getWidth(), getHeight()));
    };

    VisualField(VisualCard card, String hint, int inputType) {
        super(card.getContext());
        setSingleLine(true);
        setInputType(inputType);
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setTextColor(card.ink);
        setHintTextColor(card.secondary);
        setTextSize(14);
        setHint(hint);
        setMinHeight(card.dp(46));
        setPadding(card.dp(12), card.dp(7), card.dp(12), card.dp(7));
        setBackgroundTintList(null);
        setBackground(card.shape(card.panel, 12, card.border));
        setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});
        setOnFocusChangeListener((view, focused) -> {
            removeCallbacks(revealAfterKeyboard);
            if (focused) postDelayed(revealAfterKeyboard, 400);
        });
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(revealAfterKeyboard);
        super.onDetachedFromWindow();
    }

    void onText(Consumer<String> listener) {
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { listener.accept(text.toString()); }
        });
    }
}
