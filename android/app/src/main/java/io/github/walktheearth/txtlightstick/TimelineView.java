package io.github.walktheearth.txtlightstick;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Colour-over-time strip. Tap to add a stop, drag a stop to move it. */
class TimelineView extends View {

    interface Listener {
        int currentColor();

        void onChanged();

        void onSelected(int color);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF strip = new RectF();
    private final Path clip = new Path();
    private final float pad;
    private final float markerRadius;
    private final float touchSlop;
    private Timeline timeline = new Timeline();
    private Listener listener;
    private Timeline.Keyframe selected;
    private boolean dragging;
    private boolean moved;
    private double playhead = -1;

    TimelineView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        pad = 24 * density;
        markerRadius = 10 * density;
        touchSlop = 28 * density;
        textPaint.setColor(0xFF667085);
        textPaint.setTextSize(11 * density);
        setBackgroundColor(Color.WHITE);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setTimeline(Timeline timeline) {
        this.timeline = timeline;
        selected = null;
        invalidate();
    }

    void setPlayhead(double t) {
        playhead = t;
        invalidate();
    }

    void clearPlayhead() {
        playhead = -1;
        invalidate();
    }

    boolean hasSelection() {
        return selected != null;
    }

    void setSelectedColor(int color) {
        if (selected == null) return;
        selected.color = color;
        invalidate();
    }

    boolean deleteSelected() {
        if (selected == null) return false;
        timeline.remove(selected);
        selected = null;
        invalidate();
        if (listener != null) listener.onChanged();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        if (width <= pad * 2) return;

        float textHeight = textPaint.getTextSize();
        float textBaseline = pad + textHeight;
        strip.set(pad, pad + textHeight + 8 * getResources().getDisplayMetrics().density,
                width - pad, getHeight() - pad - textHeight - 6 * getResources().getDisplayMetrics().density);

        // Gradient preview of the whole timeline.
        canvas.save();
        clip.reset();
        clip.addRoundRect(strip, 8 * getResources().getDisplayMetrics().density,
                8 * getResources().getDisplayMetrics().density, Path.Direction.CW);
        canvas.clipPath(clip);
        float step = Math.max(1f, getResources().getDisplayMetrics().density);
        for (float x = strip.left; x < strip.right; x += step) {
            double t = timeAt(x + step / 2);
            paint.setColor(timeline.sample(t));
            canvas.drawRect(x, strip.top, Math.min(x + step, strip.right), strip.bottom, paint);
        }
        canvas.restore();

        // Strip outline.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
        paint.setColor(0xFFE4E7EC);
        canvas.drawRoundRect(strip, 8 * getResources().getDisplayMetrics().density,
                8 * getResources().getDisplayMetrics().density, paint);
        paint.setStyle(Paint.Style.FILL);

        // Keyframes.
        float centerY = strip.centerY();
        for (Timeline.Keyframe frame : timeline.keyframes) {
            float x = xAt(frame.t);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(frame.color);
            canvas.drawCircle(x, centerY, markerRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((frame == selected ? 3f : 1.5f) * getResources().getDisplayMetrics().density);
            paint.setColor(frame == selected ? 0xFF2962FF : 0x66000000);
            canvas.drawCircle(x, centerY, markerRadius, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        if (timeline.keyframes.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(0xFF667085);
            canvas.drawText("Tap anywhere to add a colour stop", strip.centerX(), centerY + textPaint.getTextSize() / 3, textPaint);
        }

        // Playhead.
        if (playhead >= 0) {
            float x = xAt(Math.min(playhead, timeline.duration));
            paint.setColor(0xFF101828);
            paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
            canvas.drawLine(x, strip.top - 6, x, strip.bottom + 6, paint);
        }

        // Axis labels.
        textPaint.setColor(0xFF667085);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("0s", strip.left, textBaseline, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.format(java.util.Locale.US, "%.0fs", timeline.duration / 2), strip.centerX(), textBaseline, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(java.util.Locale.US, "%.0fs", timeline.duration), strip.right,
                getHeight() - pad * 0.35f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) return false;
        float x = event.getX();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Timeline.Keyframe hit = nearest(x);
                if (hit != null) {
                    selected = hit;
                    dragging = true;
                    moved = false;
                    invalidate();
                    listener.onSelected(hit.color);
                } else if (strip.contains(x, event.getY())) {
                    selected = timeline.add(timeAt(x), listener.currentColor());
                    dragging = true;
                    moved = true;
                    invalidate();
                    listener.onChanged();
                } else {
                    return false;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragging && selected != null) {
                    timeline.move(selected, timeAt(x));
                    moved = true;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    dragging = false;
                    if (moved && listener != null) listener.onChanged();
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private Timeline.Keyframe nearest(float x) {
        Timeline.Keyframe best = null;
        float bestDistance = touchSlop;
        for (Timeline.Keyframe frame : timeline.keyframes) {
            float distance = Math.abs(xAt(frame.t) - x);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = frame;
            }
        }
        return best;
    }

    private float xAt(double t) {
        double fraction = Math.max(0, Math.min(1, t / timeline.duration));
        return (float) (strip.left + fraction * strip.width());
    }

    private double timeAt(float x) {
        double fraction = (x - strip.left) / strip.width();
        return Math.max(0, Math.min(1, fraction)) * timeline.duration;
    }
}
