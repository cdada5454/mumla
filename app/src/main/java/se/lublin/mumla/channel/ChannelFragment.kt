package se.lublin.mumla.channel

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.IHumlaSession
import se.lublin.humla.model.IUser
import se.lublin.humla.model.TalkState
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.humla.util.VoiceTargetMode
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.util.HumlaServiceFragment
import com.google.android.material.R as MaterialR

class ChannelFragment : HumlaServiceFragment(), SharedPreferences.OnSharedPreferenceChangeListener,
    ChatTargetProvider {

    private var viewPager: ViewPager? = null
    private var tabLayout: TabLayout? = null
    private var talkButton: View? = null
    private var talkView: View? = null
    private var talkServerView: TextView? = null
    private var talkChannelView: TextView? = null
    private var pttIdleColor = Color.TRANSPARENT
    private var pttPressedColor = Color.TRANSPARENT

    private var targetPanel: View? = null
    private var targetPanelCancel: ImageView? = null
    private var targetPanelText: TextView? = null

    private var chatTarget: ChatTargetProvider.ChatTarget? = null
    private val chatTargetListeners = mutableListOf<ChatTargetProvider.OnChatTargetSelectedListener>()

    private var talkButtonHidden = false

    private val observer: HumlaObserver = object : HumlaObserver() {
        override fun onUserTalkStateUpdated(user: IUser?) {
            val currentService = service
            if (currentService == null || !currentService.isConnected) {
                return
            }
            val selfSession: Int = try {
                currentService.HumlaSession().sessionId
            } catch (e: Exception) {
                if (e is HumlaDisconnectedException || e is IllegalStateException) {
                    Log.d(TAG, "exception in onUserTalkStateUpdated: $e")
                    return
                }
                throw e
            }
            if (user != null && user.session == selfSession) {
                when (user.talkState) {
                    TalkState.TALKING,
                    TalkState.SHOUTING,
                    TalkState.WHISPERING -> updateTalkButtonPressed(true)
                    TalkState.PASSIVE -> updateTalkButtonPressed(false)
                }
            }
        }

        override fun onUserStateUpdated(user: IUser?) {
            val currentService = service
            if (currentService == null || !currentService.isConnected) {
                return
            }
            val selfSession: Int = try {
                currentService.HumlaSession().sessionId
            } catch (e: IllegalStateException) {
                Log.d(TAG, "exception in onUserStateUpdated: $e")
                return
            }
            if (user != null && user.session == selfSession) {
                configureInput()
                activity?.supportInvalidateOptionsMenu()
            }
        }

        override fun onVoiceTargetChanged(mode: VoiceTargetMode?) {
            configureTargetPanel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ChannelLayout.create(requireContext())
        viewPager = view.findViewById(R.id.channel_view_pager)
        tabLayout = view.findViewById(R.id.channel_tab_layout)

        talkView = view.findViewById(R.id.pushtotalk_view)
        talkButton = view.findViewById(R.id.pushtotalk)
        talkServerView = view.findViewById(R.id.pushtotalk_server)
        talkChannelView = view.findViewById(R.id.pushtotalk_channel)
        pttIdleColor = ChannelLayout.resolveAppBarBackgroundColor(requireContext())
        pttPressedColor = ChannelLayout.resolveAppBarPressedColor(requireContext())
        updateTalkButtonPressed(false)
        val talkTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updateTalkButtonPressed(true)
                    service?.onTalkKeyDown()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    updateTalkButtonPressed(false)
                    service?.onTalkKeyUp()
                }
            }
            true
        }
        talkButton?.setOnTouchListener(talkTouchListener)
        talkView?.setOnTouchListener(talkTouchListener)
        targetPanel = view.findViewById(R.id.target_panel)
        targetPanelCancel = view.findViewById(R.id.target_panel_cancel)
        targetPanelCancel?.setOnClickListener {
            val currentService = service
            if (currentService == null || !currentService.isConnected) {
                return@setOnClickListener
            }
            val session: IHumlaSession = currentService.HumlaSession()
            if (session.voiceTargetMode == VoiceTargetMode.WHISPER) {
                val target = session.voiceTargetId
                session.voiceTargetId = 0.toByte()
                session.unregisterWhisperTarget(target)
            }
        }
        targetPanelText = view.findViewById(R.id.target_panel_warning)
        configureInput()
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        preferences.registerOnSharedPreferenceChangeListener(this)

        if (viewPager != null) {
            val pagerAdapter = ChannelFragmentPagerAdapter(childFragmentManager)
            viewPager?.adapter = pagerAdapter
            viewPager?.currentItem = arguments?.getInt(ARG_INITIAL_PAGE, PAGE_CHANNELS)
                ?.coerceIn(0, pagerAdapter.count - 1) ?: PAGE_CHANNELS
            tabLayout?.setupWithViewPager(viewPager)
            viewPager?.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    configureInput()
                    (activity as? MumlaActivity)?.updatePushToTalkOverlay()
                }
            })
            configureInput()
            applyInitialChatTarget()
        } else {
            val listFragment = ChannelListFragment()
            val listArgs = Bundle()
            listArgs.putBoolean("pinned", isShowingPinnedChannels())
            listFragment.arguments = listArgs
            childFragmentManager.beginTransaction()
                .replace(R.id.list_fragment, listFragment)
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.channel_input_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val settings = Settings.getInstance(requireActivity())
        return when (item.itemId) {
            R.id.menu_input_voice -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE)
                true
            }

            R.id.menu_input_ptt -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT)
                true
            }

            R.id.menu_input_continuous -> {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        val currentService = service
        if (currentService != null && currentService.isConnected &&
            !Settings.getInstance(requireActivity()).isPushToTalkToggle
        ) {
            currentService.HumlaSession().setTalkingState(false)
        }
    }

    override fun onDestroy() {
        activity?.let {
            val preferences = PreferenceManager.getDefaultSharedPreferences(it)
            preferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroy()
    }

    override fun getServiceObserver(): IHumlaObserver {
        return observer
    }

    override fun onServiceBound(service: IHumlaService?) {
        super.onServiceBound(service)
        if (service?.connectionState == HumlaService.ConnectionState.CONNECTED) {
            configureTargetPanel()
            configureInput()
            updatePushToTalkTarget()
            applyInitialChatTarget()
            activity?.supportInvalidateOptionsMenu()
        }
    }

    private fun applyInitialChatTarget() {
        val targetSession = arguments?.getInt(ARG_CHAT_TARGET_SESSION, -1) ?: -1
        if (targetSession < 0) {
            return
        }
        val currentService = service ?: return
        if (!currentService.isConnected) {
            return
        }
        val user = try {
            currentService.HumlaSession().getUser(targetSession)
        } catch (e: Exception) {
            if (e is HumlaDisconnectedException || e is IllegalStateException) {
                Log.d(TAG, "exception in applyInitialChatTarget: $e")
                null
            } else {
                throw e
            }
        } ?: return
        setChatTarget(ChatTargetProvider.ChatTarget(user))
        arguments?.remove(ARG_CHAT_TARGET_SESSION)
    }

    private fun configureTargetPanel() {
        targetPanel?.visibility = View.GONE
    }

    private fun isShowingPinnedChannels(): Boolean {
        return arguments?.getBoolean("pinned") == true
    }

    private fun configureInput() {
        val activity = activity ?: return
        val settings = Settings.getInstance(activity)
        ChannelLayout.updatePushToTalkSize(talkView)

        var muted = false
        val currentService = service
        if (currentService != null && currentService.isConnected) {
            val self: IUser? = try {
                currentService.HumlaSession().sessionUser
            } catch (e: Exception) {
                if (e is HumlaDisconnectedException || e is IllegalStateException) {
                    Log.d(TAG, "exception in configureInput: $e")
                    null
                } else {
                    throw e
                }
            }
            muted = self == null || self.isMuted || self.isSuppressed || self.isSelfMuted
        }
        val showPttButton = !muted &&
            !isShowingChatInputPage() &&
            settings.isPushToTalkButtonShown &&
            settings.inputMethod == Settings.ARRAY_INPUT_METHOD_PTT
        setTalkButtonHidden(!showPttButton)
        updatePushToTalkTarget()
    }

    fun isShowingChatInputPage(): Boolean {
        return !isShowingPinnedChannels() && viewPager?.currentItem == PAGE_CHAT
    }

    private fun updatePushToTalkTarget() {
        val currentService = service ?: return
        if (!currentService.isConnected) {
            return
        }
        val session = currentService.HumlaSession()
        val server = currentService.targetServer
        talkServerView?.text = "${server.host}:${if (server.port == 0) DEFAULT_MUMBLE_PORT else server.port}"
        talkChannelView?.text = if (session.voiceTargetMode == VoiceTargetMode.WHISPER) {
            session.whisperTarget?.name ?: session.sessionChannel.name
        } else {
            session.sessionChannel.name
        }
    }

    private fun updateTalkButtonPressed(pressed: Boolean) {
        talkButton?.isPressed = pressed
        talkButton?.setBackgroundColor(Color.TRANSPARENT)
        ChannelLayout.updatePushToTalkPressedState(talkView, pressed)
    }

    private fun setTalkButtonHidden(hidden: Boolean) {
        talkView?.visibility = if (hidden) View.GONE else View.VISIBLE
        talkButtonHidden = hidden
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Settings.PREF_INPUT_METHOD == key ||
            Settings.PREF_PUSH_BUTTON_HIDE_KEY == key ||
            Settings.PREF_PTT_BUTTON_HEIGHT == key
        ) {
            configureInput()
        }
    }

    override fun getChatTarget(): ChatTargetProvider.ChatTarget? {
        return chatTarget
    }

    override fun setChatTarget(target: ChatTargetProvider.ChatTarget?) {
        chatTarget = target
        for (listener in chatTargetListeners) {
            listener.onChatTargetSelected(target)
        }
    }

    override fun registerChatTargetListener(listener: ChatTargetProvider.OnChatTargetSelectedListener) {
        chatTargetListeners.add(listener)
    }

    override fun unregisterChatTargetListener(listener: ChatTargetProvider.OnChatTargetSelectedListener) {
        chatTargetListeners.remove(listener)
    }

    private inner class ChannelFragmentPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            val fragment: Fragment
            val args = Bundle()
            when (position) {
                0 -> {
                    fragment = ChannelListFragment()
                    args.putBoolean("pinned", isShowingPinnedChannels())
                }

                else -> {
                    fragment = ChannelChatFragment()
                }
            }
            fragment.arguments = args
            return fragment
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> getString(R.string.channel).uppercase()
                else -> getString(R.string.chat).uppercase()
            }
        }

        override fun getCount(): Int = if (isShowingPinnedChannels()) 1 else 2
    }

    companion object {
        private val TAG = ChannelFragment::class.java.name
        private const val DEFAULT_MUMBLE_PORT = 64738
        const val ARG_INITIAL_PAGE = "initial_page"
        const val ARG_CHAT_TARGET_SESSION = "chat_target_session"
        const val PAGE_CHANNELS = 0
        const val PAGE_CHAT = 1
    }
}
