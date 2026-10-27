package com.nightrider.driver

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Map-first navigation presentation, shared by live map, simulator and Field Test.
 * Banner intentionally does NOT invent turn instructions: OSRM route geometry is
 * not sufficient to promise an upcoming maneuver or an accurate ETA.
 */
class NavigationHud(
    private val activity: Activity,
    light: Boolean,
    onBack: () -> Unit,
    onRecenter: () -> Unit
) {
    private val banner = if (light) Color.rgb(0, 112, 107) else Color.rgb(31, 33, 37)
    private val bottom = if (light) Color.rgb(238, 250, 255) else Color.rgb(27, 29, 34)
    private val bottomText = if (light) Color.rgb(23, 48, 64) else Color.WHITE
    private val secondary = if (light) Color.rgb(70, 98, 113) else Color.rgb(182, 193, 201)
    val view = FrameLayout(activity)
    private val heading = text("", 23f, Color.WHITE, true)
    private val detail = text("", 12f, Color.WHITE, false)
    private val summary = text("", 23f, bottomText, true)
    private val speed = text("", 14f, bottomText, true)
    val status = text("", 12f, secondary, false)

    init {
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(14), dp(12))
            background = shape(banner, 17f)
            elevation = dp(4).toFloat()
        }
        top.addView(button("‹", onBack, true), LinearLayout.LayoutParams(dp(38), dp(64)))
        top.addView(text("↑", 33f, Color.WHITE, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(42), dp(64)))
        val textColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(heading)
        textColumn.addView(detail)
        top.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ).apply { setMargins(dp(12), dp(12), dp(12), 0) }
        view.addView(top, topParams)

        val lower = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(12), dp(12))
            background = shape(bottom, 19f)
            elevation = dp(4).toFloat()
        }
        val summaryRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        summaryRow.addView(summary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        summaryRow.addView(speed)
        summaryRow.addView(button("◎", onRecenter, false),
            LinearLayout.LayoutParams(dp(51), dp(50)).apply { leftMargin = dp(8) })
        lower.addView(summaryRow)
        lower.addView(status)
        val bottomParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { setMargins(dp(12), 0, dp(12), dp(12)) }
        view.addView(lower, bottomParams)
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun shape(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
    }
    private fun text(value: String, size: Float, color: Int, strong: Boolean) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (strong) setTypeface(null, Typeface.BOLD)
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun button(label: String, action: () -> Unit, top: Boolean) = Button(activity).apply {
        text = label; textSize = 25f; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
        setTextColor(if (top) Color.WHITE else bottomText)
        background = shape(if (top) banner else if (bottom == Color.rgb(27,29,34)) Color.rgb(49,52,58) else Color.rgb(214,232,241), 12f)
        setOnClickListener { action() }
        contentDescription = if (top) "Back" else "Recenter navigation map"
    }

    fun render(
        title: String,
        mode: String,
        meters: Double?,
        seconds: Double?,
        speedMps: Double?
    ) {
        heading.text = title
        detail.text = mode
        summary.text = if (meters != null && seconds != null && meters.isFinite() &&
            seconds.isFinite() && meters >= 0 && seconds >= 0) {
            val minutes = kotlin.math.ceil(seconds / 60).toInt()
            val miles = meters / 1609.344
            String.format(Locale.US, "%d min  ·  %.1f mi", minutes, miles)
        } else "Route preview"
        speed.text = if (speedMps != null && speedMps.isFinite() && speedMps >= 0)
            String.format(Locale.US, "%.0f mph", speedMps * 2.236936)
        else "— mph"
    }
}
