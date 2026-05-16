/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.mumla.drawable

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.TypedValue
import se.lublin.mumla.R

/**
 * A drawable containing a circular bitmap in the style of @drawable/outline_circle_talking_off.
 */
class CircleDrawable(
    private val resources: Resources,
    private val bitmap: Bitmap,
    private val strokeColor: Int = resources.getColor(R.color.ripple_talk_state_disabled)
) : Drawable() {
    private val paint = Paint().apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        isDither = true
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        isDither = true
        isAntiAlias = true
        color = strokeColor
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            STROKE_WIDTH_DP.toFloat(),
            resources.displayMetrics
        )
        style = Paint.Style.STROKE
    }
    private val drawableConstantState = object : Drawable.ConstantState() {
        override fun newDrawable(): Drawable = CircleDrawable(resources, bitmap, strokeColor)

        override fun getChangingConfigurations(): Int = 0
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        val bitmapRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val matrix = Matrix()
        matrix.setRectToRect(bitmapRect, RectF(bounds), Matrix.ScaleToFit.CENTER)
        paint.shader?.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        val imageRect = RectF(bounds)
        val strokeRect = RectF(bounds)
        // Default stroke drawing is both inset and outset.
        strokeRect.inset(strokePaint.strokeWidth / 2, strokePaint.strokeWidth / 2)

        canvas.drawOval(imageRect, paint)
        canvas.drawOval(strokeRect, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun getOpacity(): Int = PixelFormat.UNKNOWN

    override fun getConstantState(): Drawable.ConstantState = drawableConstantState

    companion object {
        const val STROKE_WIDTH_DP = 1
    }
}
