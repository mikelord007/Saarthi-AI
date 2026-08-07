package com.saarthi.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Golden pulsing border meant to be shown for the duration of a running
 * task, drawn as a plain Android [View] so it can live in a window outside
 * any Activity (an overlay survives the host Activity backgrounding, which
 * matters once a task can navigate across apps). Not wired to anything yet
 * — this is available for whichever surface ends up running tasks step by
 * step to show while it works.
 *
 * Non-interactive by construction — it only ever draws.
 */
class TaskGlowBorderView(context: Context) : View(context) {

    // Matches SaarthiColors.Accent (0xFFB68235) — kept as a raw Int here
    // since this view is built outside Compose.
    private val glowColor = Color.parseColor("#B68235")
    private val strokeWidthPx = 10f * resources.displayMetrics.density
    private val blurRadiusPx = 22f * resources.displayMetrics.density
    private val coreStrokeWidthPx = 4f * resources.displayMetrics.density
    private val coreBlurRadiusPx = 6f * resources.displayMetrics.density

    // Wide, heavily-blurred bloom.
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = glowColor
        maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }

    // Thin, lightly-blurred core drawn on top so the edge itself reads bright
    // instead of just a soft ambient wash.
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = coreStrokeWidthPx
        color = Color.WHITE
        maskFilter = BlurMaskFilter(coreBlurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }

    // BlurMaskFilter only renders on a software layer — hardware layers ignore it.
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, paint)
    }

    private val pulse = ValueAnimator.ofInt(MIN_ALPHA, MAX_ALPHA).apply {
        duration = PULSE_DURATION_MS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val alpha = it.animatedValue as Int
            paint.alpha = alpha
            corePaint.alpha = (alpha * CORE_ALPHA_SCALE).toInt()
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse.start()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx / 2f
        canvas.drawRect(inset, inset, width - inset, height - inset, paint)
        canvas.drawRect(inset, inset, width - inset, height - inset, corePaint)
    }

    private companion object {
        const val PULSE_DURATION_MS = 900L
        const val MIN_ALPHA = 120
        const val MAX_ALPHA = 255
        const val CORE_ALPHA_SCALE = 0.85f
    }
}
