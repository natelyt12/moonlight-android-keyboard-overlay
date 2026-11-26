package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/**
 * Compact, minimalist custom-drawn slider with default blue accent.
 */
public class VkSliderView extends View {

    public interface OnChangeListener {
        void onProgressChanged(VkSliderView slider, int progress, boolean fromUser);
    }

    private static final int COLOR_TRACK = 0xFF282E38;
    private static final int COLOR_THUMB = 0xFFFFFFFF;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF activeRect = new RectF();

    private int min = 0;
    private int max = 100;
    private int progress = 0;
    private boolean isDragging = false;
    private float downX;
    private float downY;
    private int touchSlop;
    private OnChangeListener listener;

    private float trackHeightPx;
    private float thumbRadiusPx;
    private float haloRadiusPx;
    private float horizPadPx;

    public VkSliderView(Context context) {
        super(context);
        init();
    }

    public VkSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VkSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        trackHeightPx = dp(3f);
        thumbRadiusPx = dp(6f);
        haloRadiusPx = dp(12f);
        horizPadPx = dp(8f);

        int accentColor = VkThemeColors.getAccentColor(getContext());

        trackPaint.setColor(COLOR_TRACK);
        trackPaint.setStyle(Paint.Style.FILL);

        activePaint.setColor(accentColor);
        activePaint.setStyle(Paint.Style.FILL);

        thumbPaint.setColor(COLOR_THUMB);
        thumbPaint.setStyle(Paint.Style.FILL);

        haloPaint.setColor(VkThemeColors.withAlpha(accentColor, 0x44));
        haloPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setMin(int min) {
        this.min = min;
        if (this.progress < min) {
            this.progress = min;
        }
        invalidate();
    }

    public void setMax(int max) {
        this.max = Math.max(min + 1, max);
        if (this.progress > this.max) {
            this.progress = this.max;
        }
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int value) {
        int clamped = Math.max(min, Math.min(max, value));
        if (this.progress != clamped) {
            this.progress = clamped;
            invalidate();
        }
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredH = (int) Math.ceil(dp(24f));
        int h = resolveSize(desiredH, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cy = h * 0.5f;
        float startX = horizPadPx;
        float endX = Math.max(startX + 1f, w - horizPadPx);
        float span = endX - startX;

        float ratio = (max > min) ? (float) (progress - min) / (float) (max - min) : 0f;
        ratio = Math.max(0f, Math.min(1f, ratio));
        float thumbX = startX + ratio * span;
        float halfTrack = trackHeightPx * 0.5f;

        trackRect.set(startX, cy - halfTrack, endX, cy + halfTrack);
        canvas.drawRoundRect(trackRect, halfTrack, halfTrack, trackPaint);

        if (thumbX > startX) {
            activeRect.set(startX, cy - halfTrack, thumbX, cy + halfTrack);
            canvas.drawRoundRect(activeRect, halfTrack, halfTrack, activePaint);
        }

        if (isDragging) {
            canvas.drawCircle(thumbX, cy, haloRadiusPx, haloPaint);
        }
        canvas.drawCircle(thumbX, cy, thumbRadiusPx, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = false;
                downX = event.getX();
                downY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (!isDragging) {
                    if (dy > touchSlop && dy > dx) {
                        // Vertical scroll intent: yield to parent ScrollView without changing value
                        return false;
                    }
                    if (dx > touchSlop && dx > dy) {
                        isDragging = true;
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        updateFromTouch(event.getX());
                        invalidate();
                    }
                    return true;
                }
                updateFromTouch(event.getX());
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    isDragging = false;
                    ViewParent p = getParent();
                    if (p != null) {
                        p.requestDisallowInterceptTouchEvent(false);
                    }
                    invalidate();
                } else {
                    // Clean stationary tap without vertical scroll
                    updateFromTouch(event.getX());
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    ViewParent p = getParent();
                    if (p != null) {
                        p.requestDisallowInterceptTouchEvent(false);
                    }
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float x) {
        float startX = horizPadPx;
        float endX = Math.max(startX + 1f, getWidth() - horizPadPx);
        float ratio = (x - startX) / (endX - startX);
        ratio = Math.max(0f, Math.min(1f, ratio));
        int newProgress = min + Math.round(ratio * (max - min));
        if (newProgress != progress) {
            progress = newProgress;
            invalidate();
            if (listener != null) {
                listener.onProgressChanged(this, progress, true);
            }
        }
    }
}
