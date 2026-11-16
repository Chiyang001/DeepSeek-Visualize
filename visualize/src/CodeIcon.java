package local.visualize;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small outline glyph; the original Compose action supplies label and touch feedback. */
public final class CodeIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean preview;

    public CodeIcon(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void bind(boolean preview, boolean dark) {
        this.preview = preview;
        paint.setColor(dark ? 0xffb5b5b5 : 0xff666666);
        invalidate();
    }

    @Override protected void onMeasure(int width, int height) {
        int size = Math.round(16 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size, width), resolveSize(size, height));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 24f, getHeight() / 24f);
        Path path = new Path();
        if (preview) {
            path.moveTo(2, 12);
            path.cubicTo(6, 4, 18, 4, 22, 12);
            path.cubicTo(18, 20, 6, 20, 2, 12);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawCircle(12, 12, 3, paint);
        } else {
            path.moveTo(12, 3); path.lineTo(12, 15);
            path.moveTo(7.5f, 10.5f); path.lineTo(12, 15); path.lineTo(16.5f, 10.5f);
            path.moveTo(4, 16); path.lineTo(4, 20); path.lineTo(20, 20); path.lineTo(20, 16);
            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }
}
