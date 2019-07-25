package com.gelort.collapsingextendtoolbarlayout

import android.view.View
import androidx.core.view.ViewCompat

internal class ViewOffsetHelper(private val view: View) {

    var layoutTop: Int = 0
        private set
    var layoutLeft: Int = 0
        private set
    private var offsetTop: Int = 0
    private var offsetLeft: Int = 0
    var isVerticalOffsetEnabled = true
    var isHorizontalOffsetEnabled = true

    fun onViewLayout() {
        // Grab the original top and left
        layoutTop = view.top
        layoutLeft = view.left
    }

    fun applyOffsets() {
        ViewCompat.offsetTopAndBottom(view, offsetTop - (view.top - layoutTop))
        ViewCompat.offsetLeftAndRight(view, offsetLeft - (view.left - layoutLeft))
    }

    /**
     * Set the top and bottom offset for this [ViewOffsetHelper]'s view.
     *
     * @param offset the offset in px.
     * @return true if the offset has changed
     */
    fun setTopAndBottomOffset(offset: Int): Boolean {
        if (isVerticalOffsetEnabled && offsetTop != offset) {
            offsetTop = offset
            applyOffsets()
            return true
        }
        return false
    }

    /**
     * Set the left and right offset for this [ViewOffsetHelper]'s view.
     *
     * @param offset the offset in px.
     * @return true if the offset has changed
     */
    fun setLeftAndRightOffset(offset: Int): Boolean {
        if (isHorizontalOffsetEnabled && offsetLeft != offset) {
            offsetLeft = offset
            applyOffsets()
            return true
        }
        return false
    }

    fun getTopAndBottomOffset(): Int {
        return offsetTop
    }

    fun getLeftAndRightOffset(): Int {
        return offsetLeft
    }
}