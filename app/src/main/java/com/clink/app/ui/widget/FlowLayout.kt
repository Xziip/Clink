package com.clink.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var horizontalGap = 8.dpToPx(context)
    var verticalGap   = 8.dpToPx(context)

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            com.clink.app.R.styleable.FlowLayout,
            0, 0
        ).apply {
            try {
                horizontalGap = getDimensionPixelSize(com.clink.app.R.styleable.FlowLayout_horizontalGap, horizontalGap)
                verticalGap = getDimensionPixelSize(com.clink.app.R.styleable.FlowLayout_verticalGap, verticalGap)
            } finally {
                recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = paddingTop + paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (lineWidth + cw > maxWidth && lineWidth > 0) {
                totalHeight += lineHeight + verticalGap
                lineWidth = cw + horizontalGap
                lineHeight = ch
            } else {
                lineWidth += cw + horizontalGap
                lineHeight = maxOf(lineHeight, ch)
            }
        }
        totalHeight += lineHeight

        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (x + cw > paddingLeft + maxWidth && x > paddingLeft) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            }
            child.layout(x, y, x + cw, y + ch)
            x += cw + horizontalGap
            lineHeight = maxOf(lineHeight, ch)
        }
    }

    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density + 0.5f).toInt()
}
