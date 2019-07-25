package com.gelort.collapsingextendtoolbarlayout;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;

public final class CollapsingTextHelper {

    // Pre-JB-MR2 doesn't support HW accelerated canvas scaled text so we will workaround it
    // by using our own texture
    private static final boolean USE_SCALING_TEXTURE = Build.VERSION.SDK_INT < 18;
    private static final boolean DEBUG_DRAW = false;
    private static final Paint DEBUG_DRAW_PAINT;
    private static final Paint DEBUG_DRAW_PAINT_C;

    static {
        DEBUG_DRAW_PAINT = DEBUG_DRAW ? new Paint() : null;
        if (DEBUG_DRAW_PAINT != null) {
            DEBUG_DRAW_PAINT.setAntiAlias(true);
            DEBUG_DRAW_PAINT.setColor(Color.MAGENTA);
        }
    }

    static {
        DEBUG_DRAW_PAINT_C = DEBUG_DRAW ? new Paint() : null;
        if (DEBUG_DRAW_PAINT_C != null) {
            DEBUG_DRAW_PAINT_C.setAntiAlias(true);
            DEBUG_DRAW_PAINT_C.setColor(Color.BLUE);
        }
    }

    private final View mView;
    private float mExpandedFraction;
    private final Rect mExpandedBounds;
    private final Rect mCollapsedBounds;
    private float mExpandedTextSize;
    private float mCollapsedTextSize;
    private Typeface mTextTypeFace;
    private int mExpandedTextColor;
    private int mCollapsedTextColor;
    private float mExpandedTop;
    private float mCollapsedTop;
    private CharSequence mText;
    private CharSequence mTextToDraw;
    private float mTextWidth;
    private boolean mUseTexture;
    private Bitmap mExpandedTitleTexture;
    private Paint mTexturePaint;
    private float mTextureAscent;
    private float mTextureDescent;
    private float mCurrentLeft;
    private float mCurrentRight;
    private float mCurrentTop;
    private float mScale;
    private float mCurrentTextSize;
    private final TextPaint mTextPaint;
    private final Paint mArrowPaint;
    private Interpolator mPositionInterpolator;
    private Interpolator mTextSizeInterpolator;

    private int mArrowWidth;
    private int mArrowHeight;
    private int[] mArrowSize = {0, 0};
    private int mArrowPaddingEnd;
    private int mArrowPadding;
    private final Path mArrowPath = new Path();
    private final Rect mRectArea = new Rect();

    public CollapsingTextHelper(View view) {
        mView = view;
        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);

        mArrowPaint = new Paint();
        mArrowPaint.setAntiAlias(true);

        mCollapsedBounds = new Rect();
        mExpandedBounds = new Rect();
    }

    void setTextSizeInterpolator(Interpolator interpolator) {
        mTextSizeInterpolator = interpolator;
        recalculate();
    }

    void setPositionInterpolator(Interpolator interpolator) {
        mPositionInterpolator = interpolator;
        recalculate();
    }

    void setCollapsedTextColor(int textColor) {
        if (mCollapsedTextColor != textColor) {
            mCollapsedTextColor = textColor;
            recalculate();
        }
    }

    void setExpandedTextColor(int textColor) {
        if (mExpandedTextColor != textColor) {
            mExpandedTextColor = textColor;
            recalculate();
        }
    }

    void setExpandedBounds(int left, int top, int right, int bottom) {
        mExpandedBounds.set(left, top, right, bottom);
        recalculate();
    }

    void setCollapsedBounds(int left, int top, int right, int bottom) {
        mCollapsedBounds.set(left, top, right, bottom);
        recalculate();
    }

    public void setArrowWidth(int mArrowWidth) {
        this.mArrowWidth = mArrowWidth;
        recalculate();
    }

    public void setArrowHeight(int mArrowHeight) {
        this.mArrowHeight = mArrowHeight;
        recalculate();
    }

    public void setArrowPadding(int mArrowPadding) {
        this.mArrowPadding = mArrowPadding;
        recalculate();
    }

    void setCollapsedTextAppearance(int resId) {
        TypedArray a = mView.getContext().obtainStyledAttributes(resId, R.styleable.TextAppearance);
        if (a.hasValue(R.styleable.TextAppearance_android_textColor)) {
            mCollapsedTextColor = a.getColor(R.styleable.TextAppearance_android_textColor, 0);
        }
        if (a.hasValue(R.styleable.TextAppearance_android_textSize)) {
            mCollapsedTextSize = a.getDimensionPixelSize(
                    R.styleable.TextAppearance_android_textSize, 0);
        }
        a.recycle();
        recalculate();
    }

    void setExpandedTextAppearance(int resId) {
        TypedArray a = mView.getContext().obtainStyledAttributes(resId, R.styleable.TextAppearance);
        if (a.hasValue(R.styleable.TextAppearance_android_textColor)) {
            mExpandedTextColor = a.getColor(R.styleable.TextAppearance_android_textColor, 0);
        }
        if (a.hasValue(R.styleable.TextAppearance_android_textSize)) {
            mExpandedTextSize = a.getDimensionPixelSize(
                    R.styleable.TextAppearance_android_textSize, 0);
        }

        a.recycle();
        recalculate();
    }

    public void setTextTypeFace(Typeface mTextTypeFace) {
        this.mTextTypeFace = mTextTypeFace;
        recalculate();
    }

    boolean isInRectArea(int touchX, int touchY) {
        float x = mCurrentLeft;
        float y = mCurrentTop;
        final boolean drawTexture = mUseTexture && mExpandedTitleTexture != null;
        final float ascent;
        final float descent;
        // Update the TextPaint to the current text size
        mTextPaint.setTextSize(mCurrentTextSize);

        if (drawTexture) {
            ascent = mTextureAscent * mScale;
            descent = mTextureDescent * mScale;
        } else {
            ascent = mTextPaint.ascent() * mScale;
            descent = mTextPaint.descent() * mScale;
        }

        mRectArea.set((int) mCurrentLeft, (int) (y + ascent), (int) mCurrentRight, (int) (y + descent));

        return mRectArea.contains(touchX, touchY);
    }


    /**
     * Set the value indicating the current scroll value. This decides how much of the
     * background will be displayed, as well as the title metrics/positioning.
     * <p>
     * A value of {@code 0.0} indicates that the layout is fully expanded.
     * A value of {@code 1.0} indicates that the layout is fully collapsed.
     */
    void setExpansionFraction(float fraction) {
        fraction = MathUtils.clamp(fraction, 0f, 1f);
        if (fraction != mExpandedFraction) {
            mExpandedFraction = fraction;
            calculateOffsets();
        }
    }

    private void calculateOffsets() {
        final float fraction = mExpandedFraction;
        mCurrentLeft = interpolate(mExpandedBounds.left, mCollapsedBounds.left,
                fraction, mPositionInterpolator);
        mCurrentTop = interpolate(mExpandedTop, mCollapsedTop, fraction, mPositionInterpolator);
        mCurrentRight = interpolate(mExpandedBounds.right, mCollapsedBounds.right,
                fraction, mPositionInterpolator);
        setInterpolatedTextSize(interpolate(mExpandedTextSize, mCollapsedTextSize,
                fraction, mTextSizeInterpolator));
        if (mCollapsedTextColor != mExpandedTextColor) {
            // If the collapsed and expanded text colors are different, blend them based on the
            // fraction
            final int color = blendColors(mExpandedTextColor, mCollapsedTextColor, fraction);
            mTextPaint.setColor(color);
            mArrowPaint.setColor(color);
        } else {
            mTextPaint.setColor(mCollapsedTextColor);
            mArrowPaint.setColor(mCollapsedTextColor);
        }
        ViewCompat.postInvalidateOnAnimation(mView);
    }

    private void calculateBaselines() {
        mTextPaint.setTextSize(mCollapsedTextSize);

        float textHeight = mTextPaint.descent() - mTextPaint.ascent();
        float textOffset = (textHeight / 2) - mTextPaint.descent();
        mCollapsedTop = mCollapsedBounds.centerY() + textOffset;

        mTextPaint.setTextSize(mExpandedTextSize);
        mTextPaint.setTypeface(mTextTypeFace);

        mExpandedTop = mExpandedBounds.bottom;

        mTextureAscent = mTextPaint.ascent();
        mTextureDescent = mTextPaint.descent();
        // The bounds have changed so we need to clear the texture
        clearTexture();
    }

    public void draw(Canvas canvas) {
        final int saveCount = canvas.save();
        if (mTextToDraw != null) {
            final boolean isRtl = ViewCompat.getLayoutDirection(mView)
                    == ViewCompat.LAYOUT_DIRECTION_RTL;
            float x = isRtl ? mCurrentRight : mCurrentLeft;
            float y = mCurrentTop;
            final boolean drawTexture = mUseTexture && mExpandedTitleTexture != null;
            final float ascent;
            final float descent;
            // Update the TextPaint to the current text size
            mTextPaint.setTextSize(mCurrentTextSize);
            if (drawTexture) {
                ascent = mTextureAscent * mScale;
                descent = mTextureDescent * mScale;
            } else {
                ascent = mTextPaint.ascent() * mScale;
                descent = mTextPaint.descent() * mScale;
            }
            if (DEBUG_DRAW) {
                // Just a debug tool, which drawn a Magneta rect in the text bounds
                canvas.drawRect(mCurrentLeft, y + ascent, mCurrentRight, y + descent,
                        DEBUG_DRAW_PAINT);

//                canvas.drawRect(mExpandedBounds, DEBUG_DRAW_PAINT_C);
                canvas.drawRect(mCollapsedBounds, DEBUG_DRAW_PAINT_C);
            }
            if (drawTexture) {
                y += ascent;
            }
            if (mScale != 1f) {
                canvas.scale(mScale, mScale, x, y);
            }
            if (isRtl) {
                x -= mTextWidth;
            }
            if (drawTexture) {
                // If we should use a texture, draw it instead of text
                canvas.drawBitmap(mExpandedTitleTexture, x, y, mTexturePaint);
            } else {
                canvas.drawText(mTextToDraw, 0, mTextToDraw.length(), x, y, mTextPaint);
            }

            final int startArrowX = (int) (x + mTextWidth + mArrowPaddingEnd);
            final int startArrowY = (int) (y - mTextPaint.descent() - mArrowSize[1] * 1.5f);

            drawArrow(canvas, startArrowX, startArrowY, mArrowSize[0], mArrowSize[1]);
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawArrow(Canvas canvas, int from_x, int from_y, int width, int height) {
        int pointX = from_x + width / 2;
        int pointY = from_y + height;

        mArrowPath.rewind();

        mArrowPath.setFillType(Path.FillType.EVEN_ODD);
        mArrowPath.moveTo(from_x, from_y);
        mArrowPath.lineTo(pointX, pointY);
        mArrowPath.lineTo(from_x + width, from_y);
        mArrowPath.close();

        canvas.drawPath(mArrowPath, mArrowPaint);
    }

    private void updateArrowSize(boolean isClosed) {
        float scale = isClosed ? (mCollapsedTextSize / mExpandedTextSize) : 1;

        mArrowSize[0] = (int) (mArrowWidth * scale);
        mArrowSize[1] = (int) (mArrowHeight * scale);

        mArrowPaddingEnd = (int) (mArrowPadding * scale);
    }

    private void setInterpolatedTextSize(final float textSize) {
        if (mText == null) return;
        final float availableWidth;
        final float newTextSize;
        boolean updateDrawText = false;

        if (isClose(textSize, mCollapsedTextSize)) {
            availableWidth = mCollapsedBounds.width();
            newTextSize = mCollapsedTextSize;
            updateArrowSize(true);
            mScale = 1f;
        } else {
            availableWidth = mExpandedBounds.width();
            newTextSize = mExpandedTextSize;
            if (isClose(textSize, mExpandedTextSize)) {
                // If we're close to the expanded text size, snap to it and use a scale of 1
                updateArrowSize(false);
                mScale = 1f;
            } else {
                updateArrowSize(false);
                // Else, we'll scale down from the expanded text size
                mScale = textSize / mExpandedTextSize;
            }
        }
        if (availableWidth > 0) {
            updateDrawText = mCurrentTextSize != newTextSize;
            mCurrentTextSize = newTextSize;
        }
        if (mTextToDraw == null || updateDrawText) {
            mTextPaint.setTextSize(mCurrentTextSize);
            // If we don't currently have text to draw, or the text size has changed, ellipsize...
            final CharSequence title = TextUtils.ellipsize(mText, mTextPaint,
                    availableWidth, TextUtils.TruncateAt.END);
            if (mTextToDraw == null || !mTextToDraw.equals(title)) {
                mTextToDraw = title;
            }
            mTextWidth = mTextPaint.measureText(mTextToDraw, 0, mTextToDraw.length());
        }
        // Use our texture if the scale isn't 1.0
        mUseTexture = USE_SCALING_TEXTURE && mScale != 1f;
        if (mUseTexture) {
            // Make sure we have an expanded texture if needed
            ensureExpandedTexture();
        }
        ViewCompat.postInvalidateOnAnimation(mView);
    }

    private void ensureExpandedTexture() {
        if (mExpandedTitleTexture != null || mExpandedBounds.isEmpty()
                || TextUtils.isEmpty(mTextToDraw)) {
            return;
        }
        mTextPaint.setTextSize(mExpandedTextSize);
        mTextPaint.setColor(mExpandedTextColor);
        final int w = Math.round(mTextPaint.measureText(mTextToDraw, 0, mTextToDraw.length()));
        final int h = Math.round(mTextPaint.descent() - mTextPaint.ascent());
        mTextWidth = w;
        if (w <= 0 && h <= 0) {
            return; // If the width or height are 0, return
        }
        mExpandedTitleTexture = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mExpandedTitleTexture);
        c.drawText(mTextToDraw, 0, mTextToDraw.length(), 0, h - mTextPaint.descent(), mTextPaint);
        if (mTexturePaint == null) {
            // Make sure we have a paint
            mTexturePaint = new Paint();
            mTexturePaint.setAntiAlias(true);
            mTexturePaint.setFilterBitmap(true);
        }
    }

    public void recalculate() {
        if (mView.getHeight() > 0 && mView.getWidth() > 0) {
            // If we've already been laid out, calculate everything now otherwise we'll wait
            // until a layout
            calculateBaselines();
            calculateOffsets();
        }
    }

    /**
     * Set the title to display
     *
     * @param text
     */
    void setText(CharSequence text) {
        if (text == null || !text.equals(mText)) {
            mText = text;
            clearTexture();
            recalculate();
        }
    }

    CharSequence getText() {
        return mText;
    }

    private void clearTexture() {
        if (mExpandedTitleTexture != null) {
            mExpandedTitleTexture.recycle();
            mExpandedTitleTexture = null;
        }
    }

    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        recalculate();
    }

    /**
     * Returns true if {@code value} is 'close' to it's closest decimal value. Close is currently
     * defined as it's difference being < 0.001.
     */
    private static boolean isClose(float value, float targetValue) {
        return Math.abs(value - targetValue) < 0.001f;
    }

    /**
     * Blend {@code color1} and {@code color2} using the given ratio.
     *
     * @param ratio of which to blend. 0.0 will return {@code color1}, 0.5 will give an even blend,
     *              1.0 will return {@code color2}.
     */
    private static int blendColors(int color1, int color2, float ratio) {
        final float inverseRatio = 1f - ratio;
        float a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio);
        float r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio);
        float g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio);
        float b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio);
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private static float interpolate(float startValue, float endValue, float fraction,
                                     Interpolator interpolator) {
        if (interpolator != null) {
            fraction = interpolator.getInterpolation(fraction);
        }
        return AnimationUtils.lerp(startValue, endValue, fraction);
    }
}