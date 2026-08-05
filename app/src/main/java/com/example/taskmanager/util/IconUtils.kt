package com.example.taskmanager.util

import android.graphics.*

object IconUtils {
    /**
     * Generates a sleek 96x96 bitmap icon showing live RAM percentage (e.g. "58%")
     * or FPS for the system status bar notification icon.
     */
    fun createMetricBitmap(text: String, percent: Float): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val color = when {
            percent > 0.85f -> Color.parseColor("#EF4444") // Danger red
            percent > 0.65f -> Color.parseColor("#F59E0B") // Warning amber
            else           -> Color.parseColor("#7C3AED") // Accent violet
        }

        // Draw background circle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, bgPaint)

        // Draw ring border
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, ringPaint)

        // Draw text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = if (text.length > 3) 28f else 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val yPos = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(text, size / 2f, yPos, textPaint)

        return bitmap
    }
}
