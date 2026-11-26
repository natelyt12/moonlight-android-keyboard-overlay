package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Compact, minimalist custom-drawn toggle switch with default blue accent.
 */
public class VkSwitchView extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(VkSwitchView switchView, boolean isChecked);
    }

    private static final int COLOR_TRACK_OFF = 0xFF1F242D;
    private static final int COLOR_BORDER_OFF = 0xFF343C4A;
    private static final int COLOR_THUMB_OFF = 0xFF9AA4B2;
    private static final int COLOR_THUMB_ON = 0xFFFFFFFF;

    private int accentColor = VkThemeColors.DEFAULT_ACCENT_BLUE;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private boolean checked = false;
    private OnCheckedChangeListener listener;

    private float widthPx;
    private float heightPx;
    private float strokePx;

    public VkSwitchView(Context context) {
        super(context);
        init();
    }

    public VkSwitchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VkSwitchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        accentColor = VkThemeColors.getAccentColor(getContext());
        widthPx = dp(34f);
        heightPx = dp(18f);
        strokePx = dp(1f);

        trackPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(strokePx);
        borderPaint.setColor(COLOR_BORDER_OFF);
        thumbPaint.setStyle(Paint.Style.FILL);

        setClickable(true);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        if (this.checked != checked) {
            this.checked = checked;
            invalidate();
            if (listener != null) {
                listener.onCheckedChanged(this, this.checked);
            }
        }
    }

    public void toggle() {
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize((int) Math.ceil(widthPx), widthMeasureSpec);
        int h = resolveSize((int) Math.ceil(heightPx), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float pad = strokePx * 0.5f;
        trackRect.set(pad, pad, w - pad, h - pad);
        float radius = h * 0.5f;

        trackPaint.setColor(checked ? accentColor : COLOR_TRACK_OFF);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        if (!checked) {
            canvas.drawRoundRect(trackRect, radius, radius, borderPaint);
        }

        float thumbRadius = (h * 0.5f) - dp(3f);
        float thumbX = checked ? (w - h * 0.5f) : (h * 0.5f);
        float thumbY = h * 0.5f;
        thumbPaint.setColor(checked ? COLOR_THUMB_ON : COLOR_THUMB_OFF);
        canvas.drawCircle(thumbX, thumbY, Math.max(1f, thumbRadius), thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            toggle();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        return super.onTouchEvent(event);
    }
}
