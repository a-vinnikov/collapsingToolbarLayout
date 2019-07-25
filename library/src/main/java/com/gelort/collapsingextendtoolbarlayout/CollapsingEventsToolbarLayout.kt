package com.gelort.collapsingextendtoolbarlayout

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.animation.AnimationUtils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.internal.ThemeEnforcement
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

class CollapsingEventsToolbarLayout : FrameLayout {

    private var mToolbar: Toolbar? = null
    private var mDummyView: View? = null

    private var mExpandedMarginLeft: Int = 0
    private var mExpandedMarginRight: Int = 0
    private var mExpandedMarginBottom: Int = 0
    private var mCollapsedMarginLeft: Int = 0
    private val mRect = Rect()
    private var mCollapsingTextHelper: CollapsingTextHelper? = null
    private var mContentScrim: Drawable? = null
    private var mStatusBarScrim: Drawable? = null
    private var mScrimAlpha: Int = 0
    private var mScrimsAreShown: Boolean = false
    private var scrimAnimator: ValueAnimator? = null
    private var scrimAlpha: Int = 0
    private var mOnOffsetChangedListener: AppBarLayout.OnOffsetChangedListener? = null
    private var mCurrentOffset: Int = 0
    private var scrimAnimationDuration: Long = 0
    private val mLastInsets: WindowInsetsCompat? = null

    @SuppressLint("RestrictedApi")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        val a = ThemeEnforcement.obtainStyledAttributes(
            context,
            attrs,
            R.styleable.CollapsingEventsToolbarLayout,
            defStyleAttr,
            com.google.android.material.R.style.Widget_Design_CollapsingToolbar
        )

        mCollapsedMarginLeft = a.getDimensionPixelSize(
            R.styleable.CollapsingEventsToolbarLayout_collapsedTitleMarginStart, 0
        )

        mExpandedMarginLeft = a.getDimensionPixelSize(
            R.styleable.CollapsingEventsToolbarLayout_expandedTitleMarginStart, 0
        )

        mExpandedMarginRight = a.getDimensionPixelSize(
            R.styleable.CollapsingEventsToolbarLayout_expandedTitleMarginEnd, 0
        )

        mExpandedMarginBottom = a.getDimensionPixelSize(
            R.styleable.CollapsingEventsToolbarLayout_expandedTitleMarginBottom, 0
        )

        mCollapsingTextHelper = CollapsingTextHelper(this)
        mCollapsingTextHelper?.setTextSizeInterpolator(AnimationUtils.DECELERATE_INTERPOLATOR as Interpolator?)

        mCollapsingTextHelper?.setExpandedTextAppearance(
            a.getResourceId(
                R.styleable.CollapsingToolbarLayout_expandedTitleTextAppearance, 0
            )
        )

        mCollapsingTextHelper?.setCollapsedTextAppearance(
            a.getResourceId(
                R.styleable.CollapsingEventsToolbarLayout_collapsedTitleTextAppearance, 0
            )
        )

        mCollapsingTextHelper?.setArrowWidth(
            a.getDimensionPixelSize(
                R.styleable.CollapsingEventsToolbarLayout_arrow_width,
                0
            )
        )

        mCollapsingTextHelper?.setArrowHeight(
            a.getDimensionPixelSize(
                R.styleable.CollapsingEventsToolbarLayout_arrow_height,
                0
            )
        )

        mCollapsingTextHelper?.setArrowPadding(
            a.getDimensionPixelSize(
                R.styleable.CollapsingEventsToolbarLayout_arrow_padding,
                0
            )
        )

        val styleAttributes = getContext().obtainStyledAttributes(
            a.getResourceId(
                R.styleable.CollapsingToolbarLayout_expandedTitleTextAppearance, 0
            ),
            R.styleable.TextAppearance
        )
        if (styleAttributes.hasValue(R.styleable.TextAppearance_fontFamily)) {
            mCollapsingTextHelper?.setTextTypeFace(
                ResourcesCompat.getFont(
                    getContext(),
                    styleAttributes.getResourceId(R.styleable.TextAppearance_fontFamily, 0)
                )
            )
        }

        styleAttributes.recycle()


        if (a.hasValue(R.styleable.CollapsingEventsToolbarLayout_toolbar_title)) {
            setTitle(a.getText(R.styleable.CollapsingEventsToolbarLayout_toolbar_title))
        }

        a.recycle()

        setWillNotDraw(false)
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}

    constructor(context: Context) : this(context, null) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val parent = getParent()

        if (parent is AppBarLayout) {
            if (mOnOffsetChangedListener == null) {
                mOnOffsetChangedListener = OffsetUpdateListener()
            }
            parent.addOnOffsetChangedListener(mOnOffsetChangedListener)
        }
    }

    override fun onDetachedFromWindow() {
        // Remove our OnOffsetChangedListener if possible and it exists
        val parent = getParent()

        if (mOnOffsetChangedListener != null && parent is AppBarLayout) {
            parent.removeOnOffsetChangedListener(mOnOffsetChangedListener)
        }

        super.onDetachedFromWindow();
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        super.addView(child, index, params)

        if (child is Toolbar) {
            mToolbar = child
            mDummyView = View(context)
            mToolbar?.addView(
                mDummyView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        // If we don't have a toolbar, the scrim will be not be drawn in drawChild() below.
        // Instead, we draw it here, before our collapsing text.
        if (mToolbar == null && mContentScrim != null && mScrimAlpha > 0) {
            mContentScrim?.mutate()?.alpha = mScrimAlpha
            mContentScrim?.draw(canvas)
        }
        // Let the collapsing text helper draw it's text
        mCollapsingTextHelper?.draw(canvas)
        // Now draw the status bar scrim
        if (mStatusBarScrim != null && mScrimAlpha > 0) {
            val topInset = mLastInsets?.systemWindowInsetTop ?: 0
            if (topInset > 0) {
                mStatusBarScrim?.setBounds(
                    0, -mCurrentOffset, width,
                    topInset - mCurrentOffset
                )
                mStatusBarScrim?.mutate()?.alpha = mScrimAlpha
                mStatusBarScrim?.draw(canvas)
            }
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        // This is a little weird. Our scrim needs to be behind the Toolbar (if it is present),
        // but in front of any other children which are behind it. To do this we intercept the
        // drawChild() call, and draw our scrim first when drawing the toolbar
        if (child === mToolbar && mContentScrim != null && mScrimAlpha > 0) {
            mContentScrim?.mutate()?.alpha = mScrimAlpha
            mContentScrim?.draw(canvas)
        }
        // Carry on drawing the child...
        return super.drawChild(canvas, child, drawingTime)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        mContentScrim?.setBounds(0, 0, w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Update our child view offset helpers
        viewLayout()

        // Now let the collapsing text helper update itself
        mCollapsingTextHelper?.onLayout(changed, left, top, right, bottom)
        // Update the collapsed bounds by getting it's transformed bounds
        ViewGroupUtils.getDescendantRect(this, mDummyView!!, mRect)
        mCollapsingTextHelper?.setCollapsedBounds(
            mCollapsedMarginLeft, bottom - mRect.height(),
            mRect.right, bottom
        )
        // Update the expanded bounds
        mCollapsingTextHelper?.setExpandedBounds(
            left + mExpandedMarginLeft, bottom - mRect.bottom,
            right - left - mExpandedMarginRight, bottom - top - mExpandedMarginBottom
        )
        // Finally, set our minimum height to enable proper AppBarLayout collapsing

        mToolbar?.let {
            minimumHeight = it.height
        }

        mCollapsingTextHelper?.recalculate()
    }

    private fun viewLayout() {
        var i = 0
        val z = childCount
        while (i < z) {
            getViewOffsetHelper(getChildAt(i)).onViewLayout()
            i++
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN -> {
                return mCollapsingTextHelper?.isInRectArea(event.x.toInt(), event.y.toInt())
                    ?: false
            }
            else -> {
                // In general, we don't want to intercept touch events. They should be
                // handled by the child view.
                false
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val touched =
                    mCollapsingTextHelper?.isInRectArea(event.x.toInt(), event.y.toInt()) ?: false

                if (touched) {
                    performClick()
                }

                return touched
            }
            else -> {

                false
            }
        }
    }

    private fun getViewOffsetHelper(view: View): ViewOffsetHelper {
        var offsetHelper: ViewOffsetHelper? =
            view.getTag(R.id.view_offset_helper) as? ViewOffsetHelper
        if (offsetHelper == null) {
            offsetHelper = ViewOffsetHelper(view)
            view.setTag(R.id.view_offset_helper, offsetHelper)
        }
        return offsetHelper
    }

    /**
     * Set the title to display
     *
     * @param title
     */
    fun setTitle(title: CharSequence) {
        mCollapsingTextHelper?.setText(title)
    }

    private fun showScrim() {
        if (!mScrimsAreShown) {
            animateScrim(255)
            mScrimsAreShown = true
        }
    }

    private fun hideScrim() {
        if (mScrimsAreShown) {
            animateScrim(0)
            mScrimsAreShown = false
        }
    }

    private fun animateScrim(targetAlpha: Int) {
        if (scrimAnimator == null) {
            scrimAnimator = ValueAnimator()
            scrimAnimator?.setDuration(scrimAnimationDuration)
            scrimAnimator?.setInterpolator(
                if (targetAlpha > scrimAlpha)
                    AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR
                else
                    AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR
            )
            scrimAnimator?.addUpdateListener(
                ValueAnimator.AnimatorUpdateListener { animator -> setScrimAlpha(animator.animatedValue as Int) })
        } else if (scrimAnimator?.isRunning() == true) {
            scrimAnimator?.cancel()
        }

        scrimAnimator?.setIntValues(scrimAlpha, targetAlpha)
        scrimAnimator?.start()
    }

    internal fun setScrimAlpha(alpha: Int) {
        if (alpha != scrimAlpha) {
            val contentScrim = this.mContentScrim
            if (contentScrim != null && mToolbar != null) {
                ViewCompat.postInvalidateOnAnimation(mToolbar!!)
            }
            scrimAlpha = alpha
            ViewCompat.postInvalidateOnAnimation(this@CollapsingEventsToolbarLayout)
        }
    }

    /**
     * Set the drawable to use for the content scrim from resources. Providing null will disable
     * the scrim functionality.
     *
     * @param drawable the drawable to display
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_contentScrim
     * @see .getContentScrim
     */
    fun setContentScrim(drawable: Drawable?) {
        if (mContentScrim !== drawable) {
            if (mContentScrim != null) {
                mContentScrim?.callback = null
            }
            mContentScrim = drawable
            drawable!!.setBounds(0, 0, width, height)
            drawable.callback = this
            drawable.mutate().alpha = mScrimAlpha
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Set the color to use for the content scrim.
     *
     * @param color the color to display
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_contentScrim
     * @see .getContentScrim
     */
    fun setContentScrimColor(color: Int) {
        setContentScrim(ColorDrawable(color))
    }

    /**
     * Set the drawable to use for the content scrim from resources.
     *
     * @param resId drawable resource id
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_contentScrim
     * @see .getContentScrim
     */
    fun setContentScrimResource(@DrawableRes resId: Int) {
        setContentScrim(ContextCompat.getDrawable(context, resId))
    }

    /**
     * Returns the drawable which is used for the foreground scrim.
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_contentScrim
     * @see .setContentScrim
     */
    fun getContentScrim(): Drawable? {
        return mContentScrim
    }

    /**
     * Set the drawable to use for the status bar scrim from resources.
     * Providing null will disable the scrim functionality.
     *
     *
     * This scrim is only shown when we have been given a top system inset.
     *
     * @param drawable the drawable to display
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_statusBarScrim
     * @see .getStatusBarScrim
     */
    fun setStatusBarScrim(drawable: Drawable?) {
        if (mStatusBarScrim !== drawable) {
            if (mStatusBarScrim != null) {
                mStatusBarScrim?.callback = null
            }
            mStatusBarScrim = drawable
            drawable!!.callback = this
            drawable.mutate().alpha = mScrimAlpha
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Set the color to use for the status bar scrim.
     *
     *
     * This scrim is only shown when we have been given a top system inset.
     *
     * @param color the color to display
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_statusBarScrim
     * @see .getStatusBarScrim
     */
    fun setStatusBarScrimColor(color: Int) {
        setStatusBarScrim(ColorDrawable(color))
    }

    /**
     * Set the drawable to use for the content scrim from resources.
     *
     * @param resId drawable resource id
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_statusBarScrim
     * @see .getStatusBarScrim
     */
    fun setStatusBarScrimResource(@DrawableRes resId: Int) {
        setStatusBarScrim(ContextCompat.getDrawable(context, resId))
    }

    /**
     * Returns the drawable which is used for the status bar scrim.
     *
     * @attr ref R.styleable#CollapsingToolbarLayout_statusBarScrim
     * @see .setStatusBarScrim
     */
    fun getStatusBarScrim(): Drawable? {
        return mStatusBarScrim
    }

    /**
     * Sets the text color and size for the collapsed title from the specified
     * TextAppearance resource.
     *
     * @attr ref android.support.design.R.styleable#CollapsingToolbarLayout_collapsedTitleTextAppearance
     */
    fun setCollapsedTitleTextAppearance(resId: Int) {
        mCollapsingTextHelper?.setCollapsedTextAppearance(resId)
    }

    /**
     * Sets the text color of the collapsed title.
     *
     * @param color The new text color in ARGB format
     */
    fun setCollapsedTitleTextColor(color: Int) {
        mCollapsingTextHelper?.setCollapsedTextColor(color)
    }

    /**
     * Sets the text color and size for the expanded title from the specified
     * TextAppearance resource.
     *
     * @attr ref android.support.design.R.styleable#CollapsingToolbarLayout_expandedTitleTextAppearance
     */
    fun setExpandedTitleTextAppearance(resId: Int) {
        mCollapsingTextHelper?.setExpandedTextAppearance(resId)
    }

    /**
     * Sets the text color of the expanded title.
     *
     * @param color The new text color in ARGB format
     */
    fun setExpandedTitleColor(color: Int) {
        mCollapsingTextHelper?.setExpandedTextColor(color)
    }

    /**
     * The additional offset used to define when to trigger the scrim visibility change.
     */
    fun getScrimTriggerOffset(): Int {
        return 2 * ViewCompat.getMinimumHeight(this)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun generateDefaultLayoutParams(): FrameLayout.LayoutParams {
        return LayoutParams(super.generateDefaultLayoutParams())
    }

    override fun generateLayoutParams(attrs: AttributeSet): FrameLayout.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): FrameLayout.LayoutParams {
        return LayoutParams(p)
    }

    class LayoutParams : FrameLayout.LayoutParams {
        /**
         * Returns the requested collapse mode.
         *
         * @return the current mode. One of [.COLLAPSE_MODE_OFF], [.COLLAPSE_MODE_PIN]
         * or [.COLLAPSE_MODE_PARALLAX].
         */
        /**
         * Set the collapse mode.
         *
         * @param collapseMode one of [.COLLAPSE_MODE_OFF], [.COLLAPSE_MODE_PIN]
         * or [.COLLAPSE_MODE_PARALLAX].
         */
        @get:CollapseMode
        var collapseMode =
            COLLAPSE_MODE_OFF
        /**
         * Returns the parallax scroll multiplier used in conjunction with
         * [.COLLAPSE_MODE_PARALLAX].
         *
         * @see .setParallaxMultiplier
         */
        /**
         * Set the parallax scroll multiplier used in conjunction with
         * [.COLLAPSE_MODE_PARALLAX]. A value of `0.0` indicates no movement at all,
         * `1.0f` indicates normal scroll movement.
         *
         * @param multiplier the multiplier.
         *
         * @see .getParallaxMultiplier
         */
        var parallaxMultiplier =
            DEFAULT_PARALLAX_MULTIPLIER

        /** @hide
         */

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.CollapsingEventsAppBarLayout_LayoutParams
            )
            collapseMode = a.getInt(
                R.styleable.CollapsingEventsAppBarLayout_LayoutParams_layout_collapseModeType,
                COLLAPSE_MODE_OFF
            )
            parallaxMultiplier = a.getFloat(
                R.styleable.CollapsingEventsAppBarLayout_LayoutParams_layout_collapseParallaxMultiplier,
                DEFAULT_PARALLAX_MULTIPLIER
            )

            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height) {}
        constructor(width: Int, height: Int, gravity: Int) : super(width, height, gravity) {}
        constructor(p: ViewGroup.LayoutParams) : super(p) {}
        constructor(source: ViewGroup.MarginLayoutParams) : super(source) {}
        constructor(source: FrameLayout.LayoutParams) : super(source) {}

        @IntDef(
            COLLAPSE_MODE_OFF,
            COLLAPSE_MODE_PIN,
            COLLAPSE_MODE_PARALLAX
        )
        @Retention(AnnotationRetention.SOURCE)
        internal annotation class CollapseMode

        companion object {
            private val DEFAULT_PARALLAX_MULTIPLIER = 0.5f
            /**
             * The view will act as normal with no collapsing behavior.
             */
            const val COLLAPSE_MODE_OFF = 0
            /**
             * The view will pin in place until it reaches the bottom of the
             * [CollapsingToolbarLayout].
             */
            const val COLLAPSE_MODE_PIN = 1
            /**
             * The view will scroll in a parallax fashion. See [.setParallaxMultiplier]
             * to change the multiplier used.
             */
            const val COLLAPSE_MODE_PARALLAX = 2
        }
    }

    private inner class OffsetUpdateListener :
        AppBarLayout.OnOffsetChangedListener {
        override fun onOffsetChanged(layout: AppBarLayout, verticalOffset: Int) {
            Timber.d("verticalOffset = $verticalOffset")
            mCurrentOffset = verticalOffset
            val insetTop = mLastInsets?.systemWindowInsetTop ?: 0
            val scrollRange = layout.totalScrollRange
            var i = 0
            val z = childCount
            while (i < z) {
                val child = getChildAt(i)
                val lp = child.layoutParams as LayoutParams
                val offsetHelper = getViewOffsetHelper(child)
                when (lp.collapseMode) {
                    LayoutParams.COLLAPSE_MODE_PIN -> if (height - insetTop + verticalOffset >= child.height) {
                        offsetHelper.setTopAndBottomOffset(-verticalOffset)
                    }
                    LayoutParams.COLLAPSE_MODE_PARALLAX -> offsetHelper.setTopAndBottomOffset(
                        (-verticalOffset * lp.parallaxMultiplier).roundToInt()
                    )
                }
                i++
            }
            // Show or hide the scrims if needed
            if (mContentScrim != null || mStatusBarScrim != null) {
                if (height + verticalOffset < getScrimTriggerOffset() + insetTop) {
                    showScrim()
                } else {
                    hideScrim()
                }
            }
            if (mStatusBarScrim != null && insetTop > 0) {
                ViewCompat.postInvalidateOnAnimation(this@CollapsingEventsToolbarLayout)
            }
            // Update the collapsing text's fraction
            val expandRange = height - ViewCompat.getMinimumHeight(
                this@CollapsingEventsToolbarLayout
            ) - insetTop
            mCollapsingTextHelper?.setExpansionFraction(
                abs(verticalOffset) / expandRange.toFloat()
            )
            if (abs(verticalOffset) == scrollRange) {
                // If we have some pinned children, and we're offset to only show those views,
                // we want to be elevate
                ViewCompat.setElevation(layout, layout.targetElevation)
            } else {
                // Otherwise, we're inline with the content
                ViewCompat.setElevation(layout, 0f)
            }
        }
    }
}