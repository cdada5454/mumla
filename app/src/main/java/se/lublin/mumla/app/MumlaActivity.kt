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

package se.lublin.mumla.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.graphics.Color
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import info.guardianproject.netcipher.proxy.OrbotHelper
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import org.spongycastle.util.encoders.Hex
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Server
import se.lublin.humla.net.HumlaConnection
import se.lublin.humla.protobuf.Mumble
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.channel.AccessTokenFragment
import se.lublin.mumla.channel.CallHistoryFragment
import se.lublin.mumla.channel.ChannelFragment
import se.lublin.mumla.channel.ChannelLayout
import se.lublin.mumla.channel.ChatHistoryFragment
import se.lublin.mumla.channel.PrivateCallFragment
import se.lublin.mumla.channel.ServerInfoFragment
import se.lublin.mumla.channel.UserProfileFragment
import se.lublin.mumla.db.DatabaseCertificate
import se.lublin.mumla.db.DatabaseProvider
import se.lublin.mumla.db.MumlaDatabase
import se.lublin.mumla.db.MumlaSQLiteDatabase
import se.lublin.mumla.preference.MumlaCertificateGenerateTask
import se.lublin.mumla.preference.SettingsActivity
import se.lublin.mumla.servers.FavouriteServerListFragment
import se.lublin.mumla.servers.ServerEditFragment
import se.lublin.mumla.service.IMumlaService
import se.lublin.mumla.service.MumlaService
import se.lublin.mumla.util.HumlaServiceFragment
import se.lublin.mumla.util.HumlaServiceProvider
import se.lublin.mumla.util.MumlaTrustStore

class MumlaActivity : AppCompatActivity(),
    DrawerAdapter.DrawerSelectionListener,
    FavouriteServerListFragment.ServerConnectHandler,
    HumlaServiceProvider,
    DatabaseProvider,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DrawerAdapter.DrawerDataProvider,
    ServerEditFragment.ServerEditListener {

    private var mumlaService: IMumlaService? = null
    override lateinit var database: MumlaDatabase
        private set
    private lateinit var settings: Settings
    private val disconnectInProgress = AtomicBoolean(false)

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var pushToTalkOverlay: PushToTalkOverlayController

    private var serverPendingPerm: Server? = null
    private var permPostNotificationsAsked = false
    private var connectingDialog: AlertDialog? = null
    private var errorDialog: AlertDialog? = null
    private var privateCallOverlay: View? = null
    private var privateCallDialogSession: Int? = null
    private var privateCallDialogState: String? = null
    private var privateCallPeerMicStatusPill: TextView? = null
    private var minimizedPrivateCallContainer: View? = null
    private var privateCallMinimized = false
    private var privateCallMinimizeAnimating = false
    private var privateCallRestoreAnimating = false
    private val serviceFragments = ArrayList<HumlaServiceFragment>()
    private val privateCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handlePrivateCallIntent(intent)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mumlaService = (service as MumlaService.MumlaBinder).service
            pushToTalkOverlay.bindService(mumlaService)
            mumlaService?.setSuppressNotifications(true)
            mumlaService?.registerObserver(observer)
            mumlaService?.clearChatNotifications()
            drawerAdapter.notifyDataSetChanged()

            for (fragment in serviceFragments) {
                fragment.setServiceBound(true)
            }

            if (supportFragmentManager.findFragmentById(R.id.content_frame) is HumlaServiceFragment &&
                mumlaService?.isConnected != true
            ) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
            updateConnectionState(getService())
            showCurrentPrivateCallDialog()
            handlePrivateCallIntent(intent)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            pushToTalkOverlay.unbindService()
            mumlaService = null
        }
    }

    private val observer = object : HumlaObserver() {
        override fun onConnected() {
            runOnMainThread {
                loadDrawerFragment(DrawerAdapter.ITEM_SERVER)
                drawerAdapter.notifyDataSetChanged()
                supportInvalidateOptionsMenu()
                updateConnectionState(getService())
            }
        }

        override fun onConnecting() {
            runOnMainThread {
                updateConnectionState(getService())
            }
        }

        override fun onDisconnected(exception: HumlaException?) {
            runOnMainThread {
                disconnectInProgress.set(false)
                if (supportFragmentManager.findFragmentById(R.id.content_frame) is HumlaServiceFragment) {
                    loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
                }
                drawerAdapter.notifyDataSetChanged()
                supportInvalidateOptionsMenu()
                updateConnectionState(getService())
            }
        }

        override fun onTLSHandshakeFailed(chain: Array<X509Certificate>) {
            if (chain.isEmpty()) {
                return
            }
            val currentService = getService() ?: return
            val lastServer = currentService.targetServer
            try {
                val x509 = chain[0]
                val layout = layoutInflater.inflate(R.layout.certificate_info, null)
                val textView = layout.findViewById<TextView>(R.id.certificate_info_text)
                try {
                    val digest1 = MessageDigest.getInstance("SHA-1")
                    val digest2 = MessageDigest.getInstance("SHA-256")
                    val hexDigest1 = String(Hex.encode(digest1.digest(x509.encoded)))
                        .replace(Regex("(..)"), "$1:")
                    val hexDigest2 = String(Hex.encode(digest2.digest(x509.encoded)))
                        .replace(Regex("(..)"), "$1:")

                    textView.text = getString(
                        R.string.certificate_info,
                        x509.subjectDN.name,
                        x509.notBefore.toString(),
                        x509.notAfter.toString(),
                        hexDigest1.substring(0, hexDigest1.length - 1),
                        hexDigest2.substring(0, hexDigest2.length - 1),
                    )
                } catch (exception: NoSuchAlgorithmException) {
                    exception.printStackTrace()
                    textView.text = x509.toString()
                }
                MaterialAlertDialogBuilder(this@MumlaActivity)
                    .setTitle(R.string.untrusted_certificate)
                    .setView(layout)
                    .setPositiveButton(R.string.allow) { _, _ ->
                        try {
                            val alias = lastServer.host
                            val trustStore: KeyStore = MumlaTrustStore.getTrustStore(this@MumlaActivity)
                            trustStore.setCertificateEntry(alias, x509)
                            MumlaTrustStore.saveTrustStore(this@MumlaActivity, trustStore)
                            Toast.makeText(this@MumlaActivity, R.string.trust_added, Toast.LENGTH_LONG).show()
                            connectToServer(lastServer)
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                            Toast.makeText(this@MumlaActivity, R.string.trust_add_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (exception: CertificateException) {
                exception.printStackTrace()
            }
        }

        override fun onPermissionDenied(reason: String) {
            MaterialAlertDialogBuilder(this@MumlaActivity)
                .setTitle(R.string.perm_denied)
                .setMessage(reason)
                .show()
        }

        override fun onUserStateUpdated(user: IUser?) {
            val updatedUser = user ?: return
            val currentService = mumlaService
            if (::drawerAdapter.isInitialized &&
                currentService?.isConnected == true &&
                updatedUser.session == currentService.HumlaSession().sessionId
            ) {
                runOnUiThread {
                    drawerAdapter.notifyDataSetChanged()
                }
            }
            if (updatedUser.session == privateCallDialogSession) {
                val name = updatedUser.name
                val muted = updatedUser.isSelfMuted
                runOnUiThread {
                    updatePrivateCallPeerMicStatus(name, muted)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings.getInstance(this)

        super.onCreate(savedInstanceState)
        configureImmersiveStatusBar()
        val mainLayout = MumlaMainLayout.create(this)
        setContentView(mainLayout.root)

        val toolbar: Toolbar = mainLayout.toolbar
        setSupportActionBar(toolbar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (privateCallOverlay != null || privateCallMinimized) {
                    if (handlePrivateCallBackPressed()) {
                        leavePrivateCallPageForBrowsing()
                    }
                    return
                }
                val currentContent = supportFragmentManager.findFragmentById(R.id.content_frame)
                if (currentContent is PrivateCallFragment) {
                    if (handlePrivateCallBackPressed()) {
                        leavePrivateCallPageForBrowsing()
                    } else {
                        loadDefaultContentFragment()
                    }
                    return
                }
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    supportFragmentManager.executePendingTransactions()
                    if (mumlaService?.isConnected == true) {
                        loadDrawerFragment(DrawerAdapter.ITEM_SERVER)
                    }
                    return
                }
                if (supportFragmentManager.findFragmentById(R.id.content_frame) is DrawerFragment) {
                    loadDefaultContentFragment()
                    return
                }
                if (supportFragmentManager.findFragmentById(R.id.content_frame) is ChannelFragment) {
                    loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
                    return
                }
                val currentService = mumlaService
                if (currentService != null && currentService.isConnected) {
                    MaterialAlertDialogBuilder(this@MumlaActivity)
                        .setMessage(getString(R.string.disconnectSure, currentService.targetServer.name))
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            disconnectAndReturnToServers(currentService)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setStayAwake(settings.shouldStayAwake())

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        database = MumlaSQLiteDatabase(this)
        database.open()

        drawerLayout = mainLayout.root
        pushToTalkOverlay = PushToTalkOverlayController(
            this,
            { mumlaService },
            { shouldShowPushToTalkOverlay() }
        ).also { it.attach() }
        val drawerList: ComposeView = mainLayout.drawer
        drawerAdapter = DrawerAdapter(this, this)
        drawerAdapter.bind(drawerList, this)
        drawerToggle = object : ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close,
        ) {
            override fun onDrawerClosed(drawerView: View) {
                supportInvalidateOptionsMenu()
                updatePushToTalkOverlay()
            }

            override fun onDrawerStateChanged(newState: Int) {
                super.onDrawerStateChanged(newState)
                updatePushToTalkOverlay()
                val currentService = getService()
                if (currentService != null && currentService.isConnected) {
                    val session = currentService.HumlaSession()
                    if (session.isTalking && !settings.isPushToTalkToggle) {
                        session.setTalkingState(false)
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                supportInvalidateOptionsMenu()
                updatePushToTalkOverlay()
            }
        }
        drawerLayout.addDrawerListener(drawerToggle)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        drawerToggle.isDrawerIndicatorEnabled = false
        drawerToggle.setHomeAsUpIndicator(R.drawable.menu_24px)
        drawerToggle.setToolbarNavigationClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)

        if (savedInstanceState == null) {
            if (intent != null && intent.hasExtra(EXTRA_DRAWER_FRAGMENT)) {
                loadDrawerFragment(intent.getIntExtra(EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_FAVOURITES))
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
        }
        handlePrivateCallIntent(intent)

        volumeControlStream = if (settings.isHandsetMode) {
            AudioManager.STREAM_VOICE_CALL
        } else {
            AudioManager.STREAM_MUSIC
        }

        if (savedInstanceState == null) {
            if (settings.isFirstRun) {
                showFirstRunGuide()
            } else {
                StartupAction().execute(this)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureImmersiveStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onResume() {
        super.onResume()
        val privateCallFilter = IntentFilter(ACTION_PRIVATE_CALL_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(privateCallReceiver, privateCallFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(privateCallReceiver, privateCallFilter)
        }
        val connectIntent = Intent(this, MumlaService::class.java)
        bindService(connectIntent, connection, 0)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            runCatching { mumlaService?.hangupPrivateCall() }
        }
        errorDialog?.dismiss()
        connectingDialog?.dismiss()

        val currentService = mumlaService
        if (currentService != null) {
            pushToTalkOverlay.unbindService()
            for (fragment in serviceFragments) {
                fragment.setServiceBound(false)
            }
            currentService.unregisterObserver(observer)
            currentService.setSuppressNotifications(false)
        }
        unbindService(connection)
        try {
            unregisterReceiver(privateCallReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrivateCallIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) {
            runCatching { mumlaService?.hangupPrivateCall() }
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        pushToTalkOverlay.detach()
        database.close()
        super.onDestroy()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val disconnectButton = menu.findItem(R.id.action_disconnect)
        disconnectButton.isVisible =
            mumlaService != null && mumlaService!!.isConnected && !disconnectInProgress.get()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.mumla, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        if (item.itemId == R.id.action_disconnect) {
            disconnectAndReturnToServers()
            return true
        }
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mumlaService != null && keyCode == settings.pushToTalkKey) {
            mumlaService?.onTalkKeyDown()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (mumlaService != null && keyCode == settings.pushToTalkKey) {
            mumlaService?.onTalkKeyUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDrawerRowClicked(id: Int) {
        if (requiresConnection(id) && !isConnected()) {
            Toast.makeText(this, R.string.server_not_connected_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        drawerLayout.closeDrawers()
        if (id == DrawerAdapter.ITEM_DISCONNECT) {
            disconnectAndReturnToServers()
            return
        }
        loadDrawerFragment(id)
    }

    private fun requiresConnection(id: Int): Boolean {
        return id == DrawerAdapter.ITEM_SERVER ||
            id == DrawerAdapter.ITEM_QR_CODE ||
            id == DrawerAdapter.ITEM_INFO ||
            id == DrawerAdapter.ITEM_DISCONNECT
    }

    override fun onDrawerProfileClicked() {
        drawerLayout.closeDrawers()
        val session = mumlaService?.takeIf { it.isConnected }?.HumlaSession()?.sessionId ?: return
        openUserProfile(session)
    }

    fun openUserProfile(session: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, UserProfileFragment.newInstance(session), UserProfileFragment::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = getString(R.string.user_profile_self_title)
        updatePushToTalkOverlay()
    }

    fun openChatWithUser(session: Int) {
        val args = Bundle().apply {
            putInt(ChannelFragment.ARG_INITIAL_PAGE, ChannelFragment.PAGE_CHAT)
            putInt(ChannelFragment.ARG_CHAT_TARGET_SESSION, session)
        }
        val fragment = instantiateFragment(ChannelFragment::class.java, args)
        drawerAdapter.setSelectedItem(DrawerAdapter.ITEM_CHAT)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, ChannelFragment::class.java.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
        supportActionBar?.title = getString(R.string.chat)
        updatePushToTalkOverlay()
    }

    fun updatePushToTalkOverlay() {
        if (::pushToTalkOverlay.isInitialized) {
            pushToTalkOverlay.update()
        }
    }

    private fun shouldShowPushToTalkOverlay(): Boolean {
        if (!::drawerLayout.isInitialized || drawerLayout.isDrawerOpen(GravityCompat.START)) {
            return false
        }
        if (privateCallOverlay != null ||
            privateCallMinimized ||
            privateCallMinimizeAnimating ||
            privateCallRestoreAnimating
        ) {
            return false
        }
        val currentContent = supportFragmentManager.findFragmentById(R.id.content_frame)
        if (currentContent is ChannelFragment && currentContent.isShowingChatInputPage()) {
            return false
        }
        val currentService = mumlaService ?: return false
        return currentService.getActivePrivateCallSession() == null &&
            currentService.getPendingIncomingPrivateCallSession() == null &&
            currentService.getPendingOutgoingPrivateCallSession() == null
    }

    override fun getDrawerProfileName(): String {
        val currentService = mumlaService
        if (currentService != null && currentService.isConnected && !disconnectInProgress.get()) {
            return try {
                currentService.HumlaSession().sessionUser?.name
                    ?: currentService.targetServer?.username
                    ?: settings.defaultUsername
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "getDrawerProfileName() while session is not ready", exception)
                currentService.targetServer?.username ?: settings.defaultUsername
            }
        }
        return settings.defaultUsername
    }

    override fun getDrawerAvatarTexture(): ByteArray? {
        val currentService = mumlaService ?: return null
        if (!currentService.isConnected) {
            return null
        }
        if (disconnectInProgress.get()) {
            return null
        }
        return try {
            currentService.HumlaSession().sessionUser?.texture
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "getDrawerAvatarTexture() while session is not ready", exception)
            null
        }
    }

    override fun getDrawerAvatarUri(): String? {
        val currentService = mumlaService ?: return null
        if (!currentService.isConnected) {
            return null
        }
        if (disconnectInProgress.get()) {
            return null
        }
        val user = try {
            currentService.HumlaSession().sessionUser ?: return null
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "getDrawerAvatarUri() while session is not ready", exception)
            return null
        }
        val serverId = currentService.targetServer?.id ?: -1
        val identity = when {
            user.hash != null -> "hash:${user.hash}"
            user.userId >= 0 -> "id:${user.userId}"
            else -> "name:${user.name}"
        }
        return getSharedPreferences(DRAWER_AVATAR_PREFS, 0)
            .getString("member_avatar:$serverId:$identity", null)
    }

    override fun onDonateClicked() {
        val stringResId = resources.getIdentifier("donate_link_foss", "string", packageName)
        if (stringResId != 0) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(stringResId)))
            startActivity(intent)
            drawerLayout.closeDrawers()
        }
    }

    private fun showFirstRunGuide() {
        if (settings.isUsingCertificate) {
            settings.setFirstRun(false)
            return
        }
        val message = getString(R.string.first_run_generate_certificate)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.first_run_generate_certificate_title)
            .setMessage(message)
            .setPositiveButton(R.string.generate) { _: DialogInterface, _: Int ->
                val generateTask = object : MumlaCertificateGenerateTask(this@MumlaActivity) {
                    override fun onPostExecute(result: DatabaseCertificate?) {
                        super.onPostExecute(result)
                        if (result != null) {
                            settings.setDefaultCertificateId(result.id)
                        }
                    }
                }
                generateTask.execute()
                settings.setFirstRun(false)
            }
            .show()
    }

    private fun loadDrawerFragment(fragmentId: Int) {
        val args = Bundle()
        val fragmentClass: Class<out Fragment> = when (fragmentId) {
            DrawerAdapter.HEADER_CONNECTED_SERVER -> FavouriteServerListFragment::class.java
            DrawerAdapter.ITEM_SERVER -> {
                args.putInt(ChannelFragment.ARG_INITIAL_PAGE, ChannelFragment.PAGE_CHANNELS)
                if (isConnected()) ChannelFragment::class.java else FavouriteServerListFragment::class.java
            }
            DrawerAdapter.ITEM_CHAT -> {
                ChatHistoryFragment::class.java
            }
            DrawerAdapter.ITEM_CALL -> CallHistoryFragment::class.java
            DrawerAdapter.ITEM_QR_CODE -> ServerQrCodeFragment::class.java
            DrawerAdapter.ITEM_INFO -> ServerInfoFragment::class.java
            DrawerAdapter.ITEM_ACCESS_TOKENS -> {
                val connectedServer = getService()!!.targetServer
                args.putLong("server", connectedServer.id)
                args.putStringArrayList("access_tokens", ArrayList(database.getAccessTokens(connectedServer.id)))
                AccessTokenFragment::class.java
            }
            DrawerAdapter.ITEM_PINNED_CHANNELS -> {
                args.putBoolean("pinned", true)
                ChannelFragment::class.java
            }
            DrawerAdapter.ITEM_FAVOURITES -> FavouriteServerListFragment::class.java
            DrawerAdapter.ITEM_SETTINGS -> {
                drawerAdapter.setSelectedItem(fragmentId)
                startActivity(Intent(this, SettingsActivity::class.java))
                return
            }
            else -> return
        }
        drawerAdapter.setSelectedItem(fragmentId)
        val fragment = instantiateFragment(fragmentClass, args)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, fragmentClass.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
        supportActionBar!!.title = getFragmentTitle(fragmentId)
        updatePushToTalkOverlay()
    }

    private fun loadDrawerPage() {
        val fragmentClass = DrawerFragment::class.java
        val fragment = instantiateFragment(fragmentClass)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, fragmentClass.name)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
        supportActionBar!!.title = getString(R.string.app_name)
        updatePushToTalkOverlay()
    }

    private fun instantiateFragment(fragmentClass: Class<out Fragment>, args: Bundle? = null): Fragment =
        fragmentClass.getDeclaredConstructor().newInstance().apply {
            arguments = args
        }

    private fun loadDefaultContentFragment() {
        loadDrawerFragment(
            if (isConnected()) {
                DrawerAdapter.ITEM_SERVER
            } else {
                DrawerAdapter.ITEM_FAVOURITES
            }
        )
    }

    private fun getFragmentTitle(fragmentId: Int): String {
        if (fragmentId == DrawerAdapter.HEADER_CONNECTED_SERVER) {
            return getString(R.string.drawer_header_servers)
        }
        val row = drawerAdapter.getItemWithId(fragmentId)
        if (row != null) {
            return row.title
        }
        val titleRes = when (fragmentId) {
            DrawerAdapter.HEADER_CONNECTED_SERVER,
            DrawerAdapter.ITEM_FAVOURITES -> R.string.drawer_header_servers
            else -> R.string.app_name
        }
        return getString(titleRes)
    }

    override fun connectToServer(server: Server) {
        serverPendingPerm = server
        connectToServerWithPerm()
    }

    fun connectToServerWithPerm() {
        val pendingServer = serverPendingPerm
        if (pendingServer == null) {
            Log.w(TAG, "No pending server before getting permissions")
            return
        }

        val currentService = mumlaService
        if (currentService != null &&
            currentService.isConnected &&
            isSameServer(pendingServer, currentService.targetServer)
        ) {
            serverPendingPerm = null
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.already_connected_to_server)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO,
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permPostNotificationsAsked) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSIONS_REQUEST_POST_NOTIFICATIONS,
                )
                return
            }
        }

        serverPendingPerm = null

        if (currentService != null && currentService.isConnected) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.reconnect_dialog_message)
                .setPositiveButton(R.string.connect) { _, _ ->
                    currentService.registerObserver(object : HumlaObserver() {
                        override fun onDisconnected(exception: HumlaException?) {
                            connectToServer(pendingServer)
                            currentService.unregisterObserver(this)
                        }
                    })
                    disconnectServiceAsync(currentService, notifyUser = false)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        if (settings.isTorEnabled) {
            if (!OrbotHelper.isOrbotInstalled(this)) {
                settings.disableTor()
                MaterialAlertDialogBuilder(this@MumlaActivity)
                    .setMessage(R.string.orbot_not_installed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            } else if (!isPortOpen(HumlaConnection.TOR_HOST, HumlaConnection.TOR_PORT, 2000)) {
                MaterialAlertDialogBuilder(this@MumlaActivity)
                    .setMessage(getString(R.string.orbot_tor_failed, HumlaConnection.TOR_PORT))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
        }

        ServerConnectTask(this, database).execute(pendingServer)
    }

    private fun isSameServer(left: Server, right: Server): Boolean {
        if (left.isSaved && right.isSaved && left.id == right.id) {
            return true
        }
        return left.host.equals(right.host, ignoreCase = true) &&
            left.port == right.port
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty()) {
            return
        }

        when (requestCode) {
            PERMISSIONS_REQUEST_RECORD_AUDIO -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectToServerWithPerm()
                } else {
                    Toast.makeText(this, getString(R.string.grant_perm_microphone), Toast.LENGTH_LONG).show()
                }
            }
            PERMISSIONS_REQUEST_POST_NOTIFICATIONS -> {
                permPostNotificationsAsked = true
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                        Toast.makeText(this, getString(R.string.grant_perm_notifications), Toast.LENGTH_LONG).show()
                    }
                }
                connectToServerWithPerm()
            }
        }
    }

    private fun isPortOpen(host: String, port: Int, timeout: Int): Boolean {
        val open = AtomicBoolean(false)
        try {
            val thread = Thread {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), timeout)
                    socket.close()
                    open.set(true)
                } catch (exception: Exception) {
                    Log.d(TAG, "isPortOpen() run()$exception")
                }
            }
            thread.start()
            thread.join()
            return open.get()
        } catch (exception: Exception) {
            Log.d(TAG, "isPortOpen() $exception")
        }
        return false
    }

    private fun setStayAwake(stayAwake: Boolean) {
        if (stayAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateConnectionState(service: IHumlaService?) {
        if (service == null) {
            return
        }

        connectingDialog?.dismiss()
        errorDialog?.dismiss()

        when (mumlaService!!.connectionState) {
            se.lublin.humla.HumlaService.ConnectionState.CONNECTING -> {
                val server = service.targetServer
                connectingDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(
                        getString(R.string.connecting_to_server, server.host) +
                            if (settings.isTorEnabled) " (Tor)" else "",
                    )
                    .setView(R.layout.dialog_progress)
                    .setCancelable(true)
                    .setOnCancelListener {
                        disconnectServiceAsync(mumlaService, notifyUser = false)
                        Toast.makeText(this@MumlaActivity, R.string.cancelled, Toast.LENGTH_SHORT).show()
                    }
                    .create()
                connectingDialog?.show()
            }
            se.lublin.humla.HumlaService.ConnectionState.CONNECTION_LOST -> {
                if (getService() != null && !getService()!!.isErrorShown()) {
                    if (getService() == null) {
                        return
                    }
                    val builder = MaterialAlertDialogBuilder(this@MumlaActivity)
                    builder.setTitle(getString(R.string.connectionRefused) + if (settings.isTorEnabled) " (Tor)" else "")
                    val error = getService()!!.connectionError
                    if (error != null && mumlaService!!.isReconnecting) {
                        builder.setMessage(
                            error.message + "\n\n" +
                                getString(
                                    R.string.attempting_reconnect,
                                    if (error.cause != null) error.cause!!.message else "unknown",
                                ),
                        )
                        builder.setPositiveButton(R.string.cancel_reconnect) { _, _ ->
                            getService()?.cancelReconnect()
                            getService()?.markErrorShown()
                        }
                    } else if (error != null &&
                        error.reason == HumlaException.HumlaDisconnectReason.REJECT &&
                        (
                            error.reject.type == Mumble.Reject.RejectType.WrongUserPW ||
                                error.reject.type == Mumble.Reject.RejectType.WrongServerPW
                            )
                    ) {
                        val passwordField = EditText(this)
                        passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        passwordField.setHint(R.string.password)
                        builder.setTitle(R.string.invalid_password)
                        builder.setMessage(error.message)
                        builder.setView(passwordField)
                        builder.setPositiveButton(R.string.reconnect) { _, _ ->
                            val server = getService()?.targetServer ?: return@setPositiveButton
                            val password = passwordField.text.toString()
                            server.password = password
                            if (server.isSaved) {
                                database.updateServer(server)
                            }
                            connectToServer(server)
                        }
                        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                            getService()?.markErrorShown()
                        }
                    } else {
                        val message = error?.message ?: getString(R.string.unknown)
                        builder.setMessage(message)
                        builder.setPositiveButton(android.R.string.ok) { _, _ ->
                            getService()?.markErrorShown()
                        }
                    }
                    builder.setCancelable(false)
                    errorDialog = builder.show()
                }
            }
            else -> Unit
        }
    }

    override fun getService(): IMumlaService? = mumlaService

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            runOnUiThread { action() }
        }
    }

    private fun disconnectAndReturnToServers(service: IHumlaService? = getService()) {
        val currentService = service
        loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
        disconnectServiceAsync(currentService)
    }

    private fun disconnectServiceAsync(
        service: IHumlaService?,
        afterRequest: (() -> Unit)? = null,
        notifyUser: Boolean = true,
    ) {
        val targetService = service ?: return
        if (!disconnectInProgress.compareAndSet(false, true)) {
            afterRequest?.invoke()
            return
        }

        supportInvalidateOptionsMenu()
        drawerAdapter.notifyDataSetChanged()
        if (notifyUser) {
            Toast.makeText(this, R.string.mumlaDisconnected, Toast.LENGTH_SHORT).show()
        }
        Thread(
            {
                var disconnectFailed = false
                try {
                    targetService.disconnect()
                } catch (exception: Exception) {
                    disconnectFailed = true
                    Log.d(TAG, "disconnectServiceAsync()", exception)
                } finally {
                    if (disconnectFailed) {
                        disconnectInProgress.set(false)
                        runOnUiThread {
                            drawerAdapter.notifyDataSetChanged()
                            supportInvalidateOptionsMenu()
                        }
                    }
                }
            },
            "MumlaDisconnect",
        ).start()
        afterRequest?.invoke()
    }

    private fun handlePrivateCallIntent(intent: Intent?) {
        val session = intent?.getIntExtra(EXTRA_PRIVATE_CALL_SESSION, -1) ?: -1
        if (session >= 0) {
            val state = intent?.getStringExtra(EXTRA_PRIVATE_CALL_STATE) ?: MumlaService.CALL_STATE_INCOMING
            showPrivateCallDialog(state, session)
            intent?.removeExtra(EXTRA_PRIVATE_CALL_SESSION)
            intent?.removeExtra(EXTRA_PRIVATE_CALL_STATE)
        }
        updatePushToTalkOverlay()
    }

    private fun showCurrentPrivateCallDialog() {
        val currentService = mumlaService ?: return
        currentService.getActivePrivateCallSession()?.let {
            showPrivateCallDialog(MumlaService.CALL_STATE_ACTIVE, it)
            return
        }
        currentService.getPendingIncomingPrivateCallSession()?.let {
            showPrivateCallDialog(MumlaService.CALL_STATE_INCOMING, it)
            return
        }
        currentService.getPendingOutgoingPrivateCallSession()?.let {
            showPrivateCallDialog(MumlaService.CALL_STATE_OUTGOING, it)
        }
    }

    private fun showPrivateCallDialog(state: String, session: Int, animateFromMinimized: Boolean = false) {
        val currentService = mumlaService ?: return
        val currentState = when {
            currentService.getActivePrivateCallSession() == session -> MumlaService.CALL_STATE_ACTIVE
            currentService.getPendingIncomingPrivateCallSession() == session -> MumlaService.CALL_STATE_INCOMING
            currentService.getPendingOutgoingPrivateCallSession() == session -> MumlaService.CALL_STATE_OUTGOING
            else -> MumlaService.CALL_STATE_ENDED
        }
        if (currentState == MumlaService.CALL_STATE_ENDED || state == MumlaService.CALL_STATE_ENDED) {
            dismissPrivateCallDialog()
            return
        }
        val callerName = runCatching {
            currentService.HumlaSession().getUser(session)?.name
        }.getOrNull() ?: session.toString()
        val caller = runCatching {
            currentService.HumlaSession().getUser(session)
        }.getOrNull()
        val avatarUri = caller?.let { localCallAvatarUri(currentService, it) }
        if (privateCallMinimized && currentState != MumlaService.CALL_STATE_INCOMING && !animateFromMinimized) {
            showMinimizedPrivateCallBar(currentState, session, callerName)
            updatePushToTalkOverlay()
            return
        }
        if (privateCallOverlay != null &&
            privateCallDialogSession == session &&
            privateCallDialogState == currentState
        ) {
            updatePushToTalkOverlay()
            return
        }

        val oldOverlay = privateCallOverlay
        if (!animateFromMinimized) {
            hideMinimizedPrivateCallBar()
            privateCallMinimized = false
        }
        privateCallDialogSession = session
        privateCallDialogState = currentState

        val overlay = createPrivateCallOverlayView(
            state = currentState,
            callerSession = session,
            callerName = callerName,
            avatarUri = avatarUri,
            onMinimize = {
                minimizePrivateCallDialog(currentState, session, callerName)
            },
            onAccept = {
                currentService.acceptPrivateCall(session)
                privateCallDialogSession = null
                privateCallDialogState = null
            },
            onReject = {
                currentService.rejectPrivateCall(session)
                privateCallDialogSession = null
                privateCallDialogState = null
            },
            onHangup = {
                currentService.hangupPrivateCall()
                privateCallDialogSession = null
                privateCallDialogState = null
            }
        )
        val parent = findViewById<FrameLayout>(android.R.id.content) ?: return
        privateCallOverlay = overlay
        if (animateFromMinimized) {
            animatePrivateCallRestore(parent, overlay)
        } else {
            (oldOverlay?.parent as? ViewGroup)?.removeView(oldOverlay)
            parent.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        updatePushToTalkOverlay()
    }

    private fun handlePrivateCallBackPressed(): Boolean {
        val currentService = mumlaService ?: return false
        val incomingSession = currentService.getPendingIncomingPrivateCallSession()
        if (privateCallOverlay != null && privateCallDialogState == MumlaService.CALL_STATE_INCOMING && incomingSession != null) {
            currentService.rejectPrivateCall(incomingSession)
            privateCallDialogSession = null
            privateCallDialogState = null
            dismissPrivateCallDialog()
            return true
        }
        val activeSession = currentService.getActivePrivateCallSession()
        val outgoingSession = currentService.getPendingOutgoingPrivateCallSession()
        val session = activeSession ?: outgoingSession ?: return false
        if (privateCallMinimized) {
            return true
        }
        val callerName = runCatching {
            currentService.HumlaSession().getUser(session)?.name
        }.getOrNull() ?: session.toString()
        val state = if (activeSession == session) {
            MumlaService.CALL_STATE_ACTIVE
        } else {
            MumlaService.CALL_STATE_OUTGOING
        }
        minimizePrivateCallDialog(state, session, callerName)
        return true
    }

    private fun leavePrivateCallPageForBrowsing() {
        window.decorView.post {
            if (!isFinishing &&
                !isDestroyed &&
                supportFragmentManager.findFragmentById(R.id.content_frame) is PrivateCallFragment
            ) {
                loadDefaultContentFragment()
            }
        }
    }

    private fun minimizePrivateCallDialog(state: String, session: Int, callerName: String) {
        if (privateCallMinimized || privateCallMinimizeAnimating || privateCallRestoreAnimating) {
            return
        }
        val overlay = privateCallOverlay
        val parent = findViewById<FrameLayout>(android.R.id.content)
        if (overlay == null || parent == null) {
            privateCallMinimized = true
            showMinimizedPrivateCallBarWhenReady(state, session, callerName)
            updatePushToTalkOverlay()
            return
        }
        privateCallMinimized = true
        privateCallMinimizeAnimating = true
        updatePushToTalkOverlay()
        val button = showMinimizedPrivateCallBar(state, session, callerName, initiallyInvisible = true) ?: run {
            privateCallMinimizeAnimating = false
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            privateCallOverlay = null
            showMinimizedPrivateCallBarWhenReady(state, session, callerName)
            updatePushToTalkOverlay()
            return
        }
        val endCard = button.findViewById<MaterialCardView>(R.id.pushtotalk_card) ?: button
        overlay.transitionName = PRIVATE_CALL_TRANSITION_NAME
        endCard.transitionName = PRIVATE_CALL_TRANSITION_NAME
        val transform = createPrivateCallContainerTransform(
            start = overlay,
            end = endCard,
            animationDuration = PRIVATE_CALL_MINIMIZE_ANIMATION_MS,
            startShape = privateCallRoundedRectangleShape(),
            endShape = privateCallCircleShape(),
            startColor = PRIVATE_CALL_OVERLAY_COLOR,
            endColor = PRIVATE_CALL_BUTTON_COLOR,
            shapeMaskProgressThresholds = MaterialContainerTransform.ProgressThresholds(0f, 0.65f)
        )
        transform.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                privateCallMinimizeAnimating = false
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                privateCallOverlay = null
                updatePushToTalkOverlay()
            }

            override fun onTransitionCancel(transition: Transition) {
                privateCallMinimizeAnimating = false
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                privateCallOverlay = null
                button.visibility = View.VISIBLE
                updatePushToTalkOverlay()
            }
        })
        TransitionManager.beginDelayedTransition(parent, transform)
        overlay.visibility = View.GONE
        button.visibility = View.VISIBLE
    }

    private fun restorePrivateCallDialogFromMinimized(state: String, session: Int) {
        if (privateCallRestoreAnimating || privateCallMinimizeAnimating) {
            return
        }
        showPrivateCallDialog(state, session, animateFromMinimized = true)
    }

    private fun animatePrivateCallRestore(parent: FrameLayout, overlay: View) {
        val button = minimizedPrivateCallContainer
        if (button == null) {
            privateCallMinimized = false
            parent.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            updatePushToTalkOverlay()
            return
        }

        privateCallRestoreAnimating = true
        updatePushToTalkOverlay()
        val startCard = button.findViewById<MaterialCardView>(R.id.pushtotalk_card) ?: button
        startCard.transitionName = PRIVATE_CALL_TRANSITION_NAME
        overlay.transitionName = PRIVATE_CALL_TRANSITION_NAME
        overlay.visibility = View.INVISIBLE
        parent.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val transform = createPrivateCallContainerTransform(
            start = startCard,
            end = overlay,
            animationDuration = PRIVATE_CALL_RESTORE_ANIMATION_MS,
            startShape = privateCallCircleShape(),
            endShape = privateCallRoundedRectangleShape(),
            startColor = PRIVATE_CALL_BUTTON_COLOR,
            endColor = PRIVATE_CALL_OVERLAY_COLOR,
            shapeMaskProgressThresholds = MaterialContainerTransform.ProgressThresholds(0.35f, 1f)
        )
        transform.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                privateCallRestoreAnimating = false
                privateCallMinimized = false
                hideMinimizedPrivateCallBar()
                overlay.visibility = View.VISIBLE
                updatePushToTalkOverlay()
            }

            override fun onTransitionCancel(transition: Transition) {
                privateCallRestoreAnimating = false
                privateCallMinimized = false
                hideMinimizedPrivateCallBar()
                overlay.visibility = View.VISIBLE
                updatePushToTalkOverlay()
            }
        })
        TransitionManager.beginDelayedTransition(parent, transform)
        button.visibility = View.GONE
        overlay.visibility = View.VISIBLE
    }

    private fun createPrivateCallContainerTransform(
        start: View,
        end: View,
        animationDuration: Long,
        startShape: ShapeAppearanceModel,
        endShape: ShapeAppearanceModel,
        startColor: Int,
        endColor: Int,
        shapeMaskProgressThresholds: MaterialContainerTransform.ProgressThresholds
    ): MaterialContainerTransform {
        return MaterialContainerTransform().apply {
            startView = start
            endView = end
            addTarget(end)
            drawingViewId = android.R.id.content
            duration = animationDuration
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            setStartShapeAppearanceModel(startShape)
            setEndShapeAppearanceModel(endShape)
            setShapeMaskProgressThresholds(shapeMaskProgressThresholds)
            setStartContainerColor(startColor)
            setEndContainerColor(endColor)
            setPathMotion(MaterialArcMotion())
        }
    }

    private fun privateCallRoundedRectangleShape(): ShapeAppearanceModel {
        return ShapeAppearanceModel.builder()
            .setAllCornerSizes(dp(PRIVATE_CALL_MINIMIZE_START_CORNER_DP).toFloat())
            .build()
    }

    private fun privateCallCircleShape(): ShapeAppearanceModel {
        return ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()
    }

    private fun showMinimizedPrivateCallBarWhenReady(
        state: String,
        session: Int,
        callerName: String,
        animateEntrance: Boolean = false
    ) {
        window.decorView.post {
            if (!isFinishing && !isDestroyed && privateCallMinimized) {
                showMinimizedPrivateCallBar(state, session, callerName, animateEntrance)
            }
        }
    }

    private fun createPrivateCallOverlayView(
        state: String,
        callerSession: Int,
        callerName: String,
        avatarUri: String?,
        onMinimize: () -> Unit,
        onAccept: () -> Unit,
        onReject: () -> Unit,
        onHangup: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            transitionName = PRIVATE_CALL_TRANSITION_NAME
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PRIVATE_CALL_OVERLAY_COLOR)
            setPadding(dp(24), statusBarHeight() + dp(18), dp(24), dp(34))
            isClickable = true
            isFocusable = true

            val titleRes = when (state) {
                MumlaService.CALL_STATE_INCOMING -> R.string.private_call_incoming_title
                MumlaService.CALL_STATE_OUTGOING -> R.string.private_call_outgoing_title
                else -> R.string.private_call_active_title
            }
            val subtitle = when (state) {
                MumlaService.CALL_STATE_INCOMING -> getString(R.string.private_call_incoming_text, callerName)
                MumlaService.CALL_STATE_OUTGOING -> getString(R.string.private_call_outgoing_text, callerName)
                else -> getString(R.string.private_call_active_text, callerName)
            }

            addView(TextView(this@MumlaActivity).apply {
                text = getString(titleRes)
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            addView(TextView(this@MumlaActivity).apply {
                text = subtitle
                setTextColor(Color.rgb(176, 190, 197))
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            addView(FrameLayout(this@MumlaActivity).apply {
                addView(MicRippleView(this@MumlaActivity) {
                    mumlaService?.getPrivateCallMicLevel() ?: 0f
                }, FrameLayout.LayoutParams(dp(330), dp(330), android.view.Gravity.CENTER))
                addView(CallAvatarView(this@MumlaActivity, callerName, avatarUri), FrameLayout.LayoutParams(dp(132), dp(132), android.view.Gravity.CENTER))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))

            val actions = LinearLayout(this@MumlaActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            if (state != MumlaService.CALL_STATE_INCOMING) {
                val pill = privateCallPeerMicStatusPill(callerName)
                privateCallPeerMicStatusPill = pill
                addView(pill, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, dp(16))
                })
                updatePrivateCallPeerMicStatus(callerName, isPrivateCallPeerSelfMuted(callerSession))
            } else {
                privateCallPeerMicStatusPill = null
            }
            when (state) {
                MumlaService.CALL_STATE_INCOMING -> {
                    actions.addView(callActionButton(R.string.private_call_reject, Color.rgb(220, 68, 55), R.drawable.cancel_24px, onReject))
                    actions.addView(callActionButton(R.string.private_call_accept, Color.rgb(32, 160, 95), R.drawable.call_24px, onAccept))
                }
                MumlaService.CALL_STATE_OUTGOING -> {
                    actions.addView(callMicrophoneToggleButton())
                    actions.addView(callAudioRouteButton())
                    actions.addView(callActionButton(R.string.private_call_hangup, Color.rgb(220, 68, 55), R.drawable.cancel_24px, onHangup))
                }
                else -> {
                    actions.addView(callMicrophoneToggleButton())
                    actions.addView(callAudioRouteButton())
                    actions.addView(callActionButton(R.string.private_call_hangup, Color.rgb(220, 68, 55), R.drawable.cancel_24px, onHangup))
                }
            }
            addView(actions, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun privateCallPeerMicStatusPill(callerName: String): TextView {
        return TextView(this).apply {
            text = getString(R.string.private_call_peer_mic_off, callerName)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(38, 50, 56))
            }
            visibility = View.GONE
        }
    }

    private fun isPrivateCallPeerSelfMuted(session: Int): Boolean {
        val currentService = mumlaService
        val user = if (currentService != null && currentService.isConnected) {
            runCatching { currentService.HumlaSession().getUser(session) }.getOrNull()
        } else {
            null
        }
        return user?.isSelfMuted == true
    }

    private fun updatePrivateCallPeerMicStatus(callerName: String, muted: Boolean) {
        privateCallPeerMicStatusPill?.apply {
            text = getString(R.string.private_call_peer_mic_off, callerName)
            visibility = if (muted) View.VISIBLE else View.GONE
        }
    }

    private fun showMinimizedPrivateCallBar(
        state: String,
        session: Int,
        callerName: String,
        animateEntrance: Boolean = false,
        initiallyInvisible: Boolean = false
    ): View? {
        hideMinimizedPrivateCallBar()
        privateCallDialogSession = session
        privateCallDialogState = state
        val openCall = {
            restorePrivateCallDialogFromMinimized(state, session)
        }
        val button = ChannelLayout.createPushToTalkPanel(this).apply {
            transitionName = PRIVATE_CALL_TRANSITION_NAME
            contentDescription = getString(R.string.private_call_active_text, callerName)
            isClickable = true
            isFocusable = true
            findViewById<MaterialCardView>(R.id.pushtotalk_card)?.setCardBackgroundColor(PRIVATE_CALL_BUTTON_COLOR)
            findViewById<ImageButton>(R.id.pushtotalk)?.apply {
                setImageResource(privateCallRouteIconRes())
                contentDescription = getString(R.string.private_call_active_title)
                setOnClickListener {
                    openCall()
                }
            }
            setOnClickListener {
                openCall()
            }
        }
        val parent = findViewById<FrameLayout>(android.R.id.content) ?: return null
        val margin = dp(16)
        val panelSize = ChannelLayout.resolvePushToTalkPanelSize(this)
        if (initiallyInvisible) {
            button.visibility = View.INVISIBLE
        }
        parent.addView(button, FrameLayout.LayoutParams(
            panelSize,
            panelSize,
            android.view.Gravity.END or android.view.Gravity.BOTTOM
        ).apply {
            setMargins(margin, margin, margin, margin)
        })
        minimizedPrivateCallContainer = button
        updatePushToTalkOverlay()
        return button
    }

    private fun hideMinimizedPrivateCallBar() {
        val container = minimizedPrivateCallContainer ?: return
        (container.parent as? ViewGroup)?.removeView(container)
        minimizedPrivateCallContainer = null
        updatePushToTalkOverlay()
    }

    private fun callActionButton(
        textRes: Int,
        color: Int,
        iconRes: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            addView(FrameLayout(this@MumlaActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                addView(ImageView(this@MumlaActivity).apply {
                    setImageResource(iconRes)
                    setColorFilter(Color.WHITE)
                }, FrameLayout.LayoutParams(dp(34), dp(34), android.view.Gravity.CENTER))
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(TextView(this@MumlaActivity).apply {
                text = getString(textRes)
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun callMicrophoneToggleButton(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            val button = FrameLayout(this@MumlaActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(55, 71, 79))
                }
            }
            val icon = ImageView(this@MumlaActivity).apply {
                setColorFilter(Color.WHITE)
            }
            val label = TextView(this@MumlaActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
            fun currentSelfMuted(): Boolean {
                val currentService = mumlaService
                val self = if (currentService != null && currentService.isConnected) {
                    currentService.HumlaSession().sessionUser
                } else {
                    null
                }
                return self?.isSelfMuted ?: settings.isMuted
            }
            var muted = currentSelfMuted()
            fun updateMicUi() {
                icon.setImageResource(if (muted) R.drawable.mic_off_24px else R.drawable.mic_24px)
                val labelRes = if (muted) R.string.private_call_mic_off else R.string.private_call_mic_on
                label.setText(labelRes)
                button.contentDescription = getString(labelRes)
            }
            button.setOnClickListener {
                val currentService = mumlaService
                val session = if (currentService != null && currentService.isConnected) {
                    currentService.HumlaSession()
                } else {
                    null
                }
                val self = session?.sessionUser
                muted = !muted
                val deafened = (self?.isSelfDeafened ?: settings.isDeafened) && muted
                session?.setSelfMuteDeafState(muted, deafened)
                settings.setMutedAndDeafened(muted, deafened)
                updateMicUi()
            }
            button.addView(icon, FrameLayout.LayoutParams(dp(34), dp(34), android.view.Gravity.CENTER))
            addView(button, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            updateMicUi()
        }
    }

    private fun callAudioRouteButton(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            val button = FrameLayout(this@MumlaActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(55, 71, 79))
                }
            }
            val icon = ImageView(this@MumlaActivity).apply {
                setColorFilter(Color.WHITE)
            }
            val label = TextView(this@MumlaActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
            fun updateRouteUi() {
                val handset = settings.isHandsetMode
                icon.setImageResource(if (handset) R.drawable.phone_in_talk_24px else R.drawable.volume_up_24px)
                val labelRes = if (handset) R.string.private_call_earpiece else R.string.private_call_speaker
                label.setText(labelRes)
                button.contentDescription = getString(labelRes)
            }
            button.setOnClickListener {
                val handset = !settings.isHandsetMode
                PreferenceManager.getDefaultSharedPreferences(this@MumlaActivity)
                    .edit()
                    .putBoolean(Settings.PREF_HANDSET_MODE, handset)
                    .apply()
                volumeControlStream = if (handset) {
                    AudioManager.STREAM_VOICE_CALL
                } else {
                    AudioManager.STREAM_MUSIC
                }
                updateRouteUi()
            }
            button.addView(icon, FrameLayout.LayoutParams(dp(34), dp(34), android.view.Gravity.CENTER))
            addView(button, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            updateRouteUi()
        }
    }

    private fun privateCallRouteIconRes(): Int {
        return if (settings.isHandsetMode) R.drawable.phone_in_talk_24px else R.drawable.volume_up_24px
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun localCallAvatarUri(service: IMumlaService, user: IUser): String? {
        val serverId = service.targetServer?.id ?: -1L
        val identity = when {
            user.hash != null -> "hash:${user.hash}"
            user.userId >= 0 -> "id:${user.userId}"
            else -> "name:${user.name}"
        }
        return getSharedPreferences(DRAWER_AVATAR_PREFS, 0)
            .getString("member_avatar:$serverId:$identity", null)
    }

    private fun dismissPrivateCallDialog() {
        privateCallOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        privateCallOverlay = null
        privateCallDialogSession = null
        privateCallDialogState = null
        privateCallPeerMicStatusPill = null
        privateCallMinimized = false
        privateCallMinimizeAnimating = false
        privateCallRestoreAnimating = false
        hideMinimizedPrivateCallBar()
        updatePushToTalkOverlay()
    }

    override fun addServiceFragment(fragment: HumlaServiceFragment) {
        serviceFragments.add(fragment)
    }

    override fun removeServiceFragment(fragment: HumlaServiceFragment) {
        serviceFragments.remove(fragment)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == null) {
            return
        }
        when (key) {
            Settings.PREF_STAY_AWAKE -> setStayAwake(settings.shouldStayAwake())
            Settings.PREF_HANDSET_MODE -> volumeControlStream = if (settings.isHandsetMode) {
                AudioManager.STREAM_VOICE_CALL
            } else {
                AudioManager.STREAM_MUSIC
            }
        }
    }

    override fun isConnected(): Boolean =
        mumlaService != null && mumlaService!!.isConnected && !disconnectInProgress.get()

    override fun getConnectedServerName(): String {
        val currentService = mumlaService
        if (currentService != null && currentService.isConnected) {
            val server = currentService.targetServer
            return if (server.name.isEmpty()) server.host else server.name
        }
        if (BuildConfig.DEBUG) {
            throw RuntimeException("getConnectedServerName should only be called if connected!")
        }
        return ""
    }

    override fun onServerEdited(action: ServerEditFragment.Action, server: Server) {
        when (action) {
            ServerEditFragment.Action.ADD_ACTION -> {
                database.addServer(server)
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
            ServerEditFragment.Action.EDIT_ACTION -> {
                database.updateServer(server)
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
        }
    }

    companion object {
        private val TAG = MumlaActivity::class.java.name

        const val EXTRA_DRAWER_FRAGMENT = "drawer_fragment"
        const val ACTION_PRIVATE_CALL_STATE = "se.lublin.mumla.app.PRIVATE_CALL_STATE"
        const val EXTRA_PRIVATE_CALL_SESSION = "private_call_session"
        const val EXTRA_PRIVATE_CALL_STATE = "private_call_state"

        private const val DRAWER_AVATAR_PREFS = "channel_member_local_avatars"
        private const val PRIVATE_CALL_MINIMIZE_ANIMATION_MS = 360L
        private const val PRIVATE_CALL_RESTORE_ANIMATION_MS = 360L
        private const val PRIVATE_CALL_MINIMIZE_START_CORNER_DP = 28
        private const val PRIVATE_CALL_TRANSITION_NAME = "private_call_container"
        private val PRIVATE_CALL_OVERLAY_COLOR = Color.rgb(18, 27, 34)
        private val PRIVATE_CALL_BUTTON_COLOR = Color.rgb(32, 160, 95)
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
        private const val PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2
    }
}

private class MicRippleView(
    context: Context,
    private val levelProvider: () -> Float
) : View(context) {
    var compact: Boolean = false
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(42, 76, 214, 145)
    }
    private val auraPath = Path()
    private var phase = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val minSize = minOf(width, height)
        val level = levelProvider().coerceIn(0f, 1f)
        val baseRadius = minSize * if (compact) 0.22f else 0.19f
        val maxExtra = minSize * if (compact) 0.18f else 0.24f

        drawAuraPath(
            path = auraPath,
            centerX = centerX,
            centerY = centerY,
            radius = baseRadius + level * maxExtra * 0.22f,
            wobble = maxExtra * (0.09f + level * 0.08f),
            phaseOffset = 0.08f,
            points = 96
        )
        canvas.drawPath(auraPath, fillPaint)

        for (i in 0 until 4) {
            val progress = (phase + i / 4f) % 1f
            val ease = 1f - (1f - progress) * (1f - progress)
            val radius = baseRadius + ease * maxExtra * (0.72f + level * 0.55f)
            val edgeFade = ((minSize * 0.49f - radius) / (minSize * 0.11f)).coerceIn(0f, 1f)
            val alpha = ((1f - progress) * edgeFade * (48f + level * 105f)).toInt().coerceIn(0, 150)
            val wobble = maxExtra * (0.045f + i * 0.012f + level * 0.055f)
            drawAuraPath(
                path = auraPath,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                wobble = wobble,
                phaseOffset = i * 0.19f,
                points = 128
            )
            auraPaint.color = Color.argb(alpha, 103, 238, 166)
            auraPaint.strokeWidth = (1.15f + level * 2.8f - i * 0.12f).coerceAtLeast(0.9f) *
                resources.displayMetrics.density
            canvas.drawPath(auraPath, auraPaint)
        }

        phase = (phase + 0.0075f + level * 0.012f) % 1f
        postInvalidateOnAnimation()
    }

    private fun drawAuraPath(
        path: Path,
        centerX: Float,
        centerY: Float,
        radius: Float,
        wobble: Float,
        phaseOffset: Float,
        points: Int
    ) {
        path.reset()
        for (index in 0..points) {
            val angle = index.toFloat() / points * TWO_PI
            val shifted = angle + (phase + phaseOffset) * TWO_PI
            val irregularity =
                kotlin.math.sin(shifted * 2.0f + 0.6f).toFloat() * 0.46f +
                    kotlin.math.sin(shifted * 3.0f - 1.1f).toFloat() * 0.30f +
                    kotlin.math.sin(shifted * 5.0f + 1.7f).toFloat() * 0.18f
            val r = radius + irregularity * wobble
            val x = centerX + kotlin.math.cos(angle) * r
            val y = centerY + kotlin.math.sin(angle) * r
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
    }

    companion object {
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}

private class CallAvatarView(
    context: Context,
    private val name: String,
    avatarUri: String?
) : View(context) {
    private val bitmap: Bitmap? = avatarUri?.let {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(it))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(37, 91, 76)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val radius = size / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val currentBitmap = bitmap
        if (currentBitmap != null) {
            val shader = BitmapShader(currentBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(size / currentBitmap.width, size / currentBitmap.height)
            val matrix = android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    centerX - currentBitmap.width * scale / 2f,
                    centerY - currentBitmap.height * scale / 2f
                )
            }
            shader.setLocalMatrix(matrix)
            imagePaint.shader = shader
            canvas.drawCircle(centerX, centerY, radius, imagePaint)
            imagePaint.shader = null
        } else {
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            textPaint.textSize = size * 0.40f
            val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", centerX, textY, textPaint)
        }

        borderPaint.strokeWidth = size * 0.028f
        canvas.drawCircle(centerX, centerY, radius - borderPaint.strokeWidth / 2f, borderPaint)
    }
}
