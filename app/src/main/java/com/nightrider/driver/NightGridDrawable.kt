package com.nightrider.driver

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Static star/sunset/grid artwork with a small scroll-linked parallax shift. */
class NightGridDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scrollOffset = 0f

    fun setParallaxOffset(value: Int) {
        val next = value.coerceAtLeast(0).toFloat()
        if (next != scrollOffset) {
            scrollOffset = next
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(3, 11, 8), Color.rgb(7, 20, 13), Color.rgb(8, 22, 14)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val starOffset = (scrollOffset * 0.025f).coerceAtMost(32f)
        paint.color = Color.argb(145, 239, 255, 247)
        val stars = floatArrayOf(
            .08f,.13f,  .19f,.28f,  .31f,.10f,  .46f,.21f,  .63f,.12f,
            .78f,.25f,  .91f,.11f,  .87f,.42f,  .15f,.43f,  .56f,.38f
        )
        for (i in stars.indices step 2) {
            canvas.drawCircle(stars[i] * w, stars[i + 1] * h + starOffset, if (i % 4 == 0) 1.6f else 1.1f, paint)
        }

        val shift = (scrollOffset * 0.075f).coerceAtMost(h * 0.12f)
        val horizon = h * 0.59f - shift
        val sunRadius = min(w * 0.22f, h * 0.17f).coerceAtLeast(24f)
        val sunX = w * 0.5f
        val sunY = horizon - sunRadius * 0.56f - scrollOffset * 0.035f

        val circle = Path().apply {
            addCircle(sunX, sunY, sunRadius, Path.Direction.CW)
        }
        val sunSave = canvas.save()
        canvas.clipPath(circle)
        paint.shader = LinearGradient(
            sunX, sunY - sunRadius, sunX, sunY + sunRadius,
            intArrayOf(Color.rgb(255, 241, 199), Color.rgb(255, 190, 94), Color.rgb(255, 111, 145)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, sunRadius, paint)
        paint.shader = null
        paint.color = Color.argb(220, 3, 11, 8)
        val stripe = sunRadius * 0.14f
        for (n in 0..3) {
            val y = sunY - sunRadius * 0.05f + n * sunRadius * 0.48f
            canvas.drawRect(sunX - sunRadius, y, sunX + sunRadius, y + stripe, paint)
        }
        canvas.restoreToCount(sunSave)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(175, 46, 230, 160)
        canvas.drawLine(0f, horizon, w, horizon, paint)

        val bottom = h * 1.03f
        paint.strokeWidth = 1.1f
        for (n in -8..8) {
            val endX = sunX + n * (w / 16f)
            paint.color = Color.argb(if (n == 0) 185 else 105, 46, 230, 160)
            canvas.drawLine(sunX, horizon, endX, bottom, paint)
        }

        for (n in 1..9) {
            val fraction = n / 9f
            val y = horizon + (bottom - horizon) * fraction * fraction
            paint.color = Color.argb((70 + n * 10).coerceAtMost(145), 46, 230, 160)
            canvas.drawLine(0f, y, w, y, paint)
        }

        val road = Path().apply {
            moveTo(sunX - sunRadius * 0.12f, horizon + 1f)
            lineTo(w * 0.18f, bottom)
            lineTo(w * 0.82f, bottom)
            lineTo(sunX + sunRadius * 0.12f, horizon + 1f)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(110, 3, 11, 8)
        canvas.drawPath(road, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(205, 255, 195, 107)
        canvas.drawLine(sunX - sunRadius * .12f, horizon, w * .18f, bottom, paint)
        paint.color = Color.argb(190, 47, 233, 255)
        canvas.drawLine(sunX + sunRadius * .12f, horizon, w * .82f, bottom, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = Color.rgb(243, 253, 251)
        canvas.drawCircle(sunX, horizon, 2.2f, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
