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
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ListView
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.channel.ChannelAdapter

class MumlaOverlay(private val service: MumlaService) {
    private val observer = object : HumlaObserver() {
        override fun onUserTalkStateUpdated(user: IUser?) {
            channelAdapter.notifyDataSetChanged()
        }

        override fun onUserStateUpdated(user: IUser?) {
            if (user == null) return
            if (user.channel != null && user.channel == service.sessionChannel) {
                channelAdapter.notifyDataSetChanged()
            }
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            if (user == null || newChannel == null || oldChannel == null) return
            val selfSession = try {
                service.sessionId
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "exception in onUserJoinedChannel: $exception")
                return
            }

            if (user.session == selfSession) {
                channelAdapter.setChannel(service.sessionChannel)
            } else if (newChannel.id == service.sessionChannel.id ||
                oldChannel.id == service.sessionChannel.id
            ) {
                channelAdapter.notifyDataSetChanged()
            }
        }
    }

    private val overlayView: View = View.inflate(service, R.layout.overlay, null)
    private val overlayList: ListView = overlayView.findViewById(R.id.overlay_list)
    private lateinit var channelAdapter: ChannelAdapter
    private val talkButton: ImageView = overlayView.findViewById(R.id.overlay_talk)
    private val closeButton: ImageView = overlayView.findViewById(R.id.overlay_close)
    private val dragButton: ImageView = overlayView.findViewById(R.id.overlay_drag)
    private val titleView: View = overlayView.findViewById(R.id.overlay_title)
    private val overlayParams: WindowManager.LayoutParams
    var isShown: Boolean = false
        private set

    init {
        titleView.setOnTouchListener(object : View.OnTouchListener {
            private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            private var initialX = 0f
            private var initialY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (MotionEvent.ACTION_DOWN == event.action) {
                    initialX = event.rawX - overlayParams.x
                    initialY = event.rawY - overlayParams.y
                    return true
                } else if (MotionEvent.ACTION_MOVE == event.action) {
                    overlayParams.x = (event.rawX - initialX).toInt()
                    overlayParams.y = (event.rawY - initialY).toInt()
                    windowManager.updateViewLayout(overlayView, overlayParams)
                    return true
                }
                return false
            }
        })

        dragButton.setOnTouchListener(object : View.OnTouchListener {
            private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            private var initialX = 0f
            private var initialY = 0f
            private var initialWidth = 0f
            private var initialHeight = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.rawX
                        initialY = event.rawY
                        initialWidth = overlayView.width.toFloat()
                        initialHeight = overlayView.height.toFloat()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        overlayParams.width = (initialWidth + (event.rawX - initialX)).toInt()
                        overlayParams.height = (initialHeight + (event.rawY - initialY)).toInt()
                        windowManager.updateViewLayout(overlayView, overlayParams)
                        return true
                    }
                }
                return false
            }
        })

        talkButton.setOnTouchListener { _, event ->
            if (MotionEvent.ACTION_DOWN == event.action) {
                service.setTalkingState(true)
                true
            } else if (MotionEvent.ACTION_UP == event.action) {
                service.setTalkingState(false)
                true
            } else {
                false
            }
        }

        val settings = Settings.getInstance(service)
        setPushToTalkShown(Settings.ARRAY_INPUT_METHOD_PTT == settings.inputMethod)

        closeButton.setOnClickListener {
            hide()
        }

        val metrics = service.resources.displayMetrics
        overlayParams = WindowManager.LayoutParams(
            (DEFAULT_WIDTH * metrics.density).toInt(),
            (DEFAULT_HEIGHT * metrics.density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        overlayParams.gravity = Gravity.TOP or Gravity.LEFT
        overlayParams.windowAnimations = android.R.style.Animation_Dialog
    }

    fun show() {
        if (isShown) {
            return
        }
        isShown = true
        channelAdapter = ChannelAdapter(service, service.sessionChannel)
        overlayList.adapter = channelAdapter
        service.registerObserver(observer)
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, overlayParams)
    }

    fun hide() {
        if (!isShown) {
            return
        }
        isShown = false
        service.unregisterObserver(observer)
        overlayList.adapter = null
        try {
            val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(overlayView)
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }
    }

    fun setPushToTalkShown(showPtt: Boolean) {
        talkButton.visibility = if (showPtt) View.VISIBLE else View.GONE
    }

    companion object {
        private val TAG = MumlaOverlay::class.java.name

        const val DEFAULT_WIDTH = 200
        const val DEFAULT_HEIGHT = 240
    }
}
