package se.lublin.mumla.app

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import se.lublin.humla.model.IUser
import se.lublin.humla.model.TalkState
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.channel.ChannelLayout
import se.lublin.mumla.service.IMumlaService

class PushToTalkOverlayController(
    private val activity: Activity,
    private val serviceProvider: () -> IMumlaService?,
    private val shouldShowOnCurrentScreen: () -> Boolean = { true }
) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val settings = Settings.getInstance(activity)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private var panel: View? = null
    private var observedService: IMumlaService? = null
    private var attached = false

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onConnected() {
            activity.runOnUiThread { update() }
        }

        override fun onDisconnected(e: HumlaException?) {
            activity.runOnUiThread {
                updatePressed(false)
                update()
            }
        }

        override fun onUserStateUpdated(user: IUser?) {
            if (isSelfUser(user)) {
                activity.runOnUiThread { update() }
            }
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            if (!isSelfUser(user)) {
                return
            }
            val pressed = when (user?.talkState) {
                TalkState.TALKING,
                TalkState.SHOUTING,
                TalkState.WHISPERING -> true
                TalkState.PASSIVE,
                null -> false
            }
            activity.runOnUiThread { updatePressed(pressed) }
        }
    }

    fun attach() {
        if (attached) {
            return
        }
        attached = true
        preferences.registerOnSharedPreferenceChangeListener(this)
        val parent = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        panel = ChannelLayout.createPushToTalkPanel(activity).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        val margin = dp(16)
        val panelSize = ChannelLayout.resolvePushToTalkPanelSize(activity)
        parent.addView(panel, FrameLayout.LayoutParams(
            panelSize,
            panelSize,
            Gravity.END or Gravity.BOTTOM
        ).apply {
            setMargins(margin, margin, margin, margin)
        })
        val touchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updatePressed(true)
                    serviceProvider()?.onTalkKeyDown()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    updatePressed(false)
                    serviceProvider()?.onTalkKeyUp()
                }
            }
            true
        }
        panel?.setOnTouchListener(touchListener)
        panel?.findViewById<View>(R.id.pushtotalk)?.setOnTouchListener(touchListener)
        update()
    }

    fun detach() {
        if (!attached) {
            return
        }
        attached = false
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        unbindService()
        (panel?.parent as? ViewGroup)?.removeView(panel)
        panel = null
    }

    fun bindService(service: IMumlaService?) {
        if (observedService === service) {
            update()
            return
        }
        unbindService()
        observedService = service
        service?.registerObserver(observer)
        update()
    }

    fun unbindService() {
        releaseTalkIfNeeded()
        observedService?.unregisterObserver(observer)
        observedService = null
        updatePressed(false)
        update()
    }

    fun update() {
        val currentPanel = panel ?: return
        ChannelLayout.updatePushToTalkSize(currentPanel)
        updatePanelPosition(currentPanel)
        val visible = shouldShow()
        if (!visible) {
            releaseTalkIfNeeded()
            updatePressed(false)
        }
        currentPanel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Settings.PREF_INPUT_METHOD ||
            key == Settings.PREF_PUSH_BUTTON_HIDE_KEY ||
            key == Settings.PREF_PTT_BUTTON_HEIGHT
        ) {
            update()
        }
    }

    private fun shouldShow(): Boolean {
        val service = serviceProvider() ?: return false
        if (!service.isConnected || !shouldShowOnCurrentScreen()) {
            return false
        }
        if (!settings.isPushToTalkButtonShown || settings.inputMethod != Settings.ARRAY_INPUT_METHOD_PTT) {
            return false
        }
        val self = try {
            service.HumlaSession().sessionUser
        } catch (e: Exception) {
            if (e is HumlaDisconnectedException || e is IllegalStateException) {
                Log.d(TAG, "session not ready for push-to-talk overlay", e)
                null
            } else {
                throw e
            }
        }
        return self != null && !self.isMuted && !self.isSuppressed && !self.isSelfMuted
    }

    private fun releaseTalkIfNeeded() {
        val service = serviceProvider()
        if (service != null && service.isConnected && !settings.isPushToTalkToggle) {
            runCatching { service.HumlaSession().setTalkingState(false) }
        }
    }

    private fun updatePressed(pressed: Boolean) {
        val currentPanel = panel
        currentPanel?.findViewById<View>(R.id.pushtotalk)?.isPressed = pressed
        currentPanel?.findViewById<View>(R.id.pushtotalk)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        ChannelLayout.updatePushToTalkPressedState(currentPanel, pressed)
    }

    private fun updatePanelPosition(currentPanel: View) {
        val panelSize = ChannelLayout.resolvePushToTalkPanelSize(activity)
        val margin = dp(16)
        currentPanel.layoutParams = (currentPanel.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = panelSize
            height = panelSize
            gravity = Gravity.END or Gravity.BOTTOM
            setMargins(margin, margin, margin, margin)
        } ?: currentPanel.layoutParams
    }

    private fun isSelfUser(user: IUser?): Boolean {
        val service = serviceProvider()
        if (service == null || !service.isConnected || user == null) {
            return false
        }
        return try {
            user.session == service.HumlaSession().sessionId
        } catch (e: IllegalStateException) {
            Log.d(TAG, "session not ready while checking push-to-talk self user", e)
            false
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private val TAG = PushToTalkOverlayController::class.java.name
    }
}
