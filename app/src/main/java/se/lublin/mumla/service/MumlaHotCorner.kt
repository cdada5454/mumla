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

package se.lublin.mumla.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import se.lublin.mumla.R

class MumlaHotCorner(
    private val context: Context,
    gravity: Int,
    private val listener: MumlaHotCornerListener,
) : View.OnTouchListener {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view: View = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        .inflate(R.layout.ptt_corner, null, false)
    private var shown = false
    private val highlightColour = ContextCompat.getColor(context, R.color.hot_corner_highlight)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
    }

    init {
        view.setOnTouchListener(this)
    }

    private fun updateLayout() {
        if (!isShown()) {
            return
        }
        windowManager.updateViewLayout(view, params)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                this.view.setBackgroundColor(highlightColour)
                listener.onHotCornerDown()
                true
            }
            MotionEvent.ACTION_UP -> {
                this.view.setBackgroundColor(0)
                listener.onHotCornerUp()
                true
            }
            else -> false
        }
    }

    fun setShown(shown: Boolean) {
        if (shown == this.shown) {
            return
        }
        if (shown) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    val showSetting = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    showSetting.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(showSetting)
                    Toast.makeText(context, R.string.grant_perm_draw_over_apps, Toast.LENGTH_LONG).show()
                    return
                }
            }
            windowManager.addView(view, params)
        } else {
            windowManager.removeView(view)
        }
        this.shown = shown
    }

    fun isShown(): Boolean = shown

    var gravity: Int
        get() = params.gravity
        set(gravity) {
            params.gravity = gravity
            updateLayout()
        }

    interface MumlaHotCornerListener {
        fun onHotCornerDown()
        fun onHotCornerUp()
    }
}
