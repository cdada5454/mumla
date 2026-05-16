package se.lublin.mumla.preference

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import se.lublin.mumla.R
import se.lublin.mumla.app.PushToTalkOverlayController
import se.lublin.mumla.service.IMumlaService
import se.lublin.mumla.service.MumlaService

class SettingsActivity : AppCompatActivity() {
    private var mumlaService: IMumlaService? = null
    private var serviceBound = false
    private lateinit var pushToTalkOverlay: PushToTalkOverlayController

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mumlaService = (service as MumlaService.MumlaBinder).service
            pushToTalkOverlay.bindService(mumlaService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            pushToTalkOverlay.unbindService()
            mumlaService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = SettingsLayout.create(this)
        setContentView(layout.root)
        pushToTalkOverlay = PushToTalkOverlayController(
            this,
            { mumlaService },
            { shouldShowPushToTalkOverlay() }
        ).also { it.attach() }

        setSupportActionBar(layout.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.action_settings)

        layout.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!supportFragmentManager.popBackStackImmediate()) {
                    finish()
                }
            }
        })

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, RootPreferenceFragment())
                .commit()
        }

        supportFragmentManager.setFragmentResultListener("launchFragment", this) { _, result ->
            val fragmentClassName = result.getString("fragmentClassName")
            if (fragmentClassName != null) {
                try {
                    val fragmentClass = Class.forName(fragmentClassName)
                    val fragment = fragmentClass.getDeclaredConstructor().newInstance() as Fragment
                    fragment.arguments = result
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings_container, fragment)
                        .addToBackStack(null)
                        .commit()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!serviceBound) {
            serviceBound = bindService(Intent(this, MumlaService::class.java), connection, 0)
        }
    }

    override fun onPause() {
        pushToTalkOverlay.unbindService()
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        mumlaService = null
        super.onPause()
    }

    override fun onDestroy() {
        pushToTalkOverlay.detach()
        super.onDestroy()
    }

    private fun shouldShowPushToTalkOverlay(): Boolean {
        val currentService = mumlaService ?: return false
        return currentService.getActivePrivateCallSession() == null &&
            currentService.getPendingIncomingPrivateCallSession() == null &&
            currentService.getPendingOutgoingPrivateCallSession() == null
    }

    class RootPreferenceFragment : MumlaPreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preference_headers, rootKey)
        }
    }
}

private object SettingsLayout {
    fun create(activity: AppCompatActivity): Views {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val appBar = AppBarLayout(ContextThemeWrapper(activity, R.style.TweakedAppBarLayout)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val toolbar = Toolbar(activity).apply {
            val popupOverlay = android.util.TypedValue()
            if (activity.theme.resolveAttribute(R.attr.popupOverlay, popupOverlay, true)) {
                popupTheme = popupOverlay.resourceId
            }
            layoutParams = AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize)).let {
                    try {
                        it.getDimensionPixelSize(0, 0)
                    } finally {
                        it.recycle()
                    }
                }
            )
        }

        val container = FrameLayout(activity).apply {
            id = R.id.settings_container
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        appBar.addView(toolbar)
        root.addView(appBar)
        root.addView(container)
        return Views(root, toolbar)
    }

    class Views(
        val root: LinearLayout,
        val toolbar: Toolbar
    )
}
