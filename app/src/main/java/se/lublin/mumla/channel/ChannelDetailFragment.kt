package se.lublin.mumla.channel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.preference.PreferenceManager
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Server
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.humla.model.TalkState
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.util.HumlaServiceFragment

class ChannelDetailFragment : HumlaServiceFragment(), ChatTargetProvider,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var viewPager: ViewPager? = null
    private var talkView: View? = null
    private var talkButton: View? = null
    private var channelId = -1
    private var chatTarget: ChatTargetProvider.ChatTarget? = null
    private val listeners = mutableListOf<ChatTargetProvider.OnChatTargetSelectedListener>()
    private var preferences: SharedPreferences? = null
    private var pttIdleColor = Color.TRANSPARENT
    private var pttPressedColor = Color.TRANSPARENT

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            activity?.supportFragmentManager?.popBackStack()
        }

        override fun onChannelStateUpdated(channel: IChannel?) {
            if (channel != null && channel.id == channelId) {
                updateChannelTarget()
            }
        }

        override fun onUserStateUpdated(user: IUser?) {
            if (isSelfUser(user)) {
                configureInput()
                activity?.supportInvalidateOptionsMenu()
            }
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            if (!isSelfUser(user)) {
                return
            }
            when (user?.talkState) {
                TalkState.TALKING,
                TalkState.SHOUTING,
                TalkState.WHISPERING -> updateTalkButtonPressed(true)
                TalkState.PASSIVE,
                null -> updateTalkButtonPressed(false)
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        channelId = arguments?.getInt(ARG_CHANNEL_ID, -1) ?: -1
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        preferences = PreferenceManager.getDefaultSharedPreferences(context).also {
            it.registerOnSharedPreferenceChangeListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateChannelTarget()
        configureInput()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = FrameLayout(requireContext()).apply {
            clipToPadding = false
            clipChildren = false
        }
        val contentView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        viewPager = ViewPager(requireContext()).apply {
            id = R.id.channel_detail_view_pager
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentView.addView(viewPager)
        view.addView(contentView)
        talkView = ChannelLayout.createPushToTalkPanel(requireContext()).apply {
            val margin = dpToPx(16f)
            layoutParams = FrameLayout.LayoutParams(
                ChannelLayout.resolvePushToTalkPanelSize(requireContext()),
                ChannelLayout.resolvePushToTalkPanelSize(requireContext()),
                android.view.Gravity.END or android.view.Gravity.BOTTOM
            ).apply {
                setMargins(margin, margin, margin, margin)
            }
        }
        talkButton = talkView?.findViewById(R.id.pushtotalk)
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
        view.addView(talkView)

        val pagerAdapter = ChannelDetailPagerAdapter(childFragmentManager)
        viewPager?.adapter = pagerAdapter
        viewPager?.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                updateTalkViewForPage(position)
            }
        })
        updateTalkViewForPage(0)

        if (service?.isConnected == true) {
            updateChannelTarget()
        }
        return view
    }

    override fun onServiceBound(service: IHumlaService?) {
        updateChannelTarget()
        if (service?.connectionState == HumlaService.ConnectionState.CONNECTED) {
            configureInput()
        }
        activity?.supportInvalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        ChannelAudioMenu.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        ChannelAudioMenu.prepare(requireContext(), service, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (ChannelAudioMenu.handle(requireContext(), service, item)) {
            activity?.supportInvalidateOptionsMenu()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun getServiceObserver(): IHumlaObserver {
        return observer
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
        preferences?.unregisterOnSharedPreferenceChangeListener(this)
        preferences = null
        super.onDestroy()
    }

    private fun updateChannelTarget() {
        val currentService = service ?: return
        if (!currentService.isConnected) {
            return
        }

        val session = currentService.HumlaSession()
        val channel = session.getChannel(channelId) ?: return

        setChatTarget(ChatTargetProvider.ChatTarget(channel))
        val server: Server? = currentService.targetServer
        val currentActivity = activity
        if (server != null && currentActivity is AppCompatActivity) {
            val address = "${server.host}:${if (server.port == 0) DEFAULT_MUMBLE_PORT else server.port}"
            val actionBar: ActionBar? = currentActivity.supportActionBar
            actionBar?.title = channel.name
            actionBar?.subtitle = formatServerAddressSubtitle(address)
        } else if (currentActivity != null && currentActivity !is AppCompatActivity) {
            currentActivity.title = channel.name
        } else if (currentActivity is AppCompatActivity) {
            val actionBar: ActionBar? = currentActivity.supportActionBar
            actionBar?.title = channel.name
            actionBar?.subtitle = null
        }
    }

    private fun configureInput() {
        ChannelLayout.updatePushToTalkSize(talkView)
        talkView?.visibility = View.GONE
    }

    private fun updateTalkViewForPage(position: Int) {
        talkView?.visibility = View.GONE
    }

    private fun updateTalkButtonPressed(pressed: Boolean) {
        talkButton?.isPressed = pressed
        talkButton?.setBackgroundColor(Color.TRANSPARENT)
        ChannelLayout.updatePushToTalkPressedState(talkView, pressed)
    }

    private fun isSelfUser(user: IUser?): Boolean {
        val currentService = service
        if (currentService == null || !currentService.isConnected || user == null) {
            return false
        }
        return try {
            user.session == currentService.HumlaSession().sessionId
        } catch (e: IllegalStateException) {
            Log.d(TAG, "exception in isSelfUser: $e")
            false
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Settings.PREF_INPUT_METHOD == key ||
            Settings.PREF_PUSH_BUTTON_HIDE_KEY == key ||
            Settings.PREF_PTT_BUTTON_HEIGHT == key
        ) {
            configureInput()
        }
    }

    override fun onDestroyView() {
        val currentActivity = activity
        if (currentActivity is AppCompatActivity) {
            currentActivity.supportActionBar?.subtitle = null
        }
        super.onDestroyView()
    }

    override fun getChatTarget(): ChatTargetProvider.ChatTarget? {
        return chatTarget
    }

    override fun setChatTarget(target: ChatTargetProvider.ChatTarget?) {
        chatTarget = target
        for (listener in listeners) {
            listener.onChatTargetSelected(target)
        }
    }

    override fun registerChatTargetListener(listener: ChatTargetProvider.OnChatTargetSelectedListener) {
        listeners.add(listener)
    }

    override fun unregisterChatTargetListener(listener: ChatTargetProvider.OnChatTargetSelectedListener) {
        listeners.remove(listener)
    }

    private inner class ChannelDetailPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> ChannelMembersFragment.newInstance(channelId)
                else -> ChannelChatFragment()
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> getString(R.string.members).uppercase()
                else -> getString(R.string.chat).uppercase()
            }
        }

        override fun getCount(): Int = 2
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun formatServerAddressSubtitle(address: String): CharSequence {
        return SpannableString(address).apply {
            setSpan(RelativeSizeSpan(0.82f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(CHANNEL_ADDRESS_SUBTITLE_COLOR), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val TAG = ChannelDetailFragment::class.java.name
        private const val ARG_CHANNEL_ID = "channel_id"
        private const val DEFAULT_MUMBLE_PORT = 64738
        private const val CHAT_PAGE_INDEX = 1
        private const val CHANNEL_ADDRESS_SUBTITLE_COLOR = 0x99FFFFFF.toInt()

        @JvmStatic
        fun newInstance(channelId: Int): ChannelDetailFragment {
            val fragment = ChannelDetailFragment()
            fragment.arguments = Bundle().apply { putInt(ARG_CHANNEL_ID, channelId) }
            return fragment
        }
    }
}
