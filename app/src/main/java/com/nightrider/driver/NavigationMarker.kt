package com.nightrider.driver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import kotlin.math.roundToInt

/** Small, legible vehicle position marker, independent of bundled map style. */
object NavigationMarker {
    fun create(context: Context, light: Boolean): Icon {
        val scale = context.resources.displayMetrics.density
        val size = (58 * scale).roundToInt()
        val middle = size / 2f
        val canvas = Canvas(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawing = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (light) Color.rgb(14, 33, 49) else Color.rgb(22, 28, 40)
        drawing.drawCircle(middle, middle, 22 * scale, paint)
        paint.color = if (light) Color.rgb(73, 157, 255) else Color.rgb(177, 207, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f * scale
        drawing.drawCircle(middle, middle, 21 * scale, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val arrow = Path().apply {
            moveTo(middle, middle - 16 * scale)
            lineTo(middle + 12 * scale, middle + 12 * scale)
            lineTo(middle, middle + 5 * scale)
            lineTo(middle - 12 * scale, middle + 12 * scale)
            close()
        }
        drawing.drawPath(arrow, paint)
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }
}
