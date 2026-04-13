package com.coderang.letterboxdwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import kotlin.math.min

class WallpaperBitmapFormatter(private val context: Context) {
    fun fitPosterToScreen(poster: Bitmap): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1080)
        val height = metrics.heightPixels.coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawColor(ContextCompat.getColor(context, R.color.wallpaper_backdrop))

        val scale = min(
            width.toFloat() / poster.width.toFloat(),
            height.toFloat() / poster.height.toFloat(),
        )
        val scaledWidth = poster.width * scale
        val scaledHeight = poster.height * scale
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f

        canvas.drawBitmap(
            poster,
            null,
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            paint,
        )

        return bitmap
    }
}
