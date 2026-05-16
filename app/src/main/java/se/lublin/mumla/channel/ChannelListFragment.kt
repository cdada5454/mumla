package se.lublin.mumla.channel

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.CursorWrapper
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import se.lublin.humla.IHumlaService
import se.lublin.humla.IHumlaSession
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.humla.util.VoiceTargetMode
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.db.DatabaseProvider
import se.lublin.mumla.util.HumlaServiceFragment

class ChannelListFragment : HumlaServiceFragment(), OnChannelClickListener, OnUserClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var channelView: RecyclerView? = null
    private var channelListAdapter: ChannelListAdapter? = null
    private var targetProvider: ChatTargetProvider? = null
    private var databaseProvider: DatabaseProvider? = null
    private var actionMode: ActionMode? = null
    private lateinit var settings: Settings

    private val serviceObserver: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            channelView?.adapter = null
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()

            val currentService = service
            if (currentService == null || !currentService.isConnected || user == null || newChannel == null) {
                return
            }

            val selfSession: Int = try {
                currentService.HumlaSession().sessionId
            } catch (e: Exception) {
                if (e is HumlaDisconnectedException || e is IllegalStateException) {
                    Log.d(TAG, "exception in onUserJoinedChannel: $e")
                    return
                }
                throw e
            }

            if (user.session == selfSession) {
                scrollToChannel(newChannel.id)
            }
        }

        override fun onChannelAdded(channel: IChannel?) {
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()
        }

        override fun onChannelRemoved(channel: IChannel?) {
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()
        }

        override fun onChannelStateUpdated(channel: IChannel?) {
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()
        }

        override fun onUserConnected(user: IUser?) {
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            val currentService = service
            if (currentService == null || !currentService.isConnected) {
                return
            }
            channelListAdapter?.updateChannels()
            channelListAdapter?.notifyDataSetChanged()
        }

        override fun onUserStateUpdated(user: IUser?) {
            channelListAdapter?.updateUserStates(user, channelView)
            activity?.supportInvalidateOptionsMenu()
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            channelListAdapter?.updateUserStates(user, channelView)
        }

        override fun onVoiceTargetChanged(mode: VoiceTargetMode?) {
            channelListAdapter?.notifyDataSetChanged()
        }
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activity?.supportInvalidateOptionsMenu()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        targetProvider = parentFragment as? ChatTargetProvider
            ?: throw ClassCastException("${parentFragment} must implement ChatTargetProvider")
        databaseProvider = activity as? DatabaseProvider
            ?: throw ClassCastException("${activity} must implement DatabaseProvider")
        settings = Settings.getInstance(context)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ChannelListLayout.createRecyclerView(requireContext())
        channelView = view
        channelView?.layoutManager = LinearLayoutManager(activity)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        channelView?.let { registerForContextMenu(it) }
        val currentActivity = activity ?: return
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentActivity.registerReceiver(bluetoothReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            currentActivity.registerReceiver(bluetoothReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        val currentActivity = activity ?: return
        currentActivity.setTitle(if (isShowingPinnedChannels()) R.string.drawer_pinned else R.string.drawer_server)
        if (currentActivity is AppCompatActivity) {
            val actionBar: ActionBar? = currentActivity.supportActionBar
            actionBar?.subtitle = null
        }
    }

    override fun onDetach() {
        activity?.unregisterReceiver(bluetoothReceiver)
        super.onDetach()
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentActivity = activity ?: return
        val preferences = PreferenceManager.getDefaultSharedPreferences(currentActivity)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun getServiceObserver(): IHumlaObserver {
        return serviceObserver
    }

    override fun onServiceBound(service: IHumlaService?) {
        try {
            if (channelListAdapter == null) {
                setupChannelList()
            } else if (service != null) {
                channelListAdapter?.setService(service)
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val currentService = service
        val bluetoothItem = menu.findItem(R.id.menu_bluetooth) ?: return
        bluetoothItem.isVisible = currentService != null && currentService.isConnected
        bluetoothItem.isChecked = false
        if (currentService != null && currentService.isConnected) {
            try {
                bluetoothItem.isChecked = currentService.HumlaSession().usingBluetoothSco()
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "Unable to prepare bluetooth menu while disconnecting: $exception")
                bluetoothItem.isVisible = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu)

        val searchItem = menu.findItem(R.id.menu_search)
        val currentActivity = activity ?: return
        val searchManager = currentActivity.getSystemService(Context.SEARCH_SERVICE) as SearchManager

        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(currentActivity.componentName))
        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val currentService = service
                if (currentService == null || !currentService.isConnected) {
                    return false
                }
                val cursor = searchView.suggestionsAdapter.getItem(position) as CursorWrapper
                val typeColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA)
                val dataIdColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                val itemType = cursor.getString(typeColumn)
                val itemId = cursor.getInt(dataIdColumn)

                val session: IHumlaSession = currentService.HumlaSession()
                if (ChannelSearchProvider.INTENT_DATA_CHANNEL == itemType) {
                    if (session.sessionChannel.id != itemId) {
                        session.joinChannel(itemId)
                    } else {
                        scrollToChannel(itemId)
                    }
                    return true
                } else if (ChannelSearchProvider.INTENT_DATA_USER == itemType) {
                    scrollToUser(itemId)
                    return true
                }
                return false
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return super.onOptionsItemSelected(item)
        }

        return when (item.itemId) {
            R.id.menu_search -> false
            R.id.menu_bluetooth -> {
                try {
                    val session: IHumlaSession = currentService.HumlaSession()
                    item.isChecked = !item.isChecked
                    if (item.isChecked) {
                        session.enableBluetoothSco()
                    } else {
                        session.disableBluetoothSco()
                    }
                } catch (exception: IllegalStateException) {
                    Log.d(TAG, "Unable to toggle bluetooth while disconnecting: $exception")
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @Throws(RemoteException::class)
    private fun setupChannelList() {
        val currentActivity = activity ?: return
        val service = service ?: return
        val database = databaseProvider?.database ?: return
        channelListAdapter = ChannelListAdapter(
            currentActivity, service, database, childFragmentManager,
            isShowingPinnedChannels(), settings.shouldShowUserCount()
        )
        channelListAdapter?.setOnChannelClickListener(this)
        channelListAdapter?.setOnUserClickListener(this)
        channelView?.adapter = channelListAdapter
        channelListAdapter?.notifyDataSetChanged()
    }

    fun scrollToChannel(channelId: Int) {
        val channelPosition = channelListAdapter?.getChannelPosition(channelId) ?: return
        channelView?.scrollToPosition(channelPosition)
    }

    fun scrollToUser(userId: Int) {
        val userPosition = channelListAdapter?.getUserPosition(userId) ?: return
        channelView?.scrollToPosition(userPosition)
    }

    private fun isShowingPinnedChannels(): Boolean {
        return arguments?.getBoolean("pinned") == true
    }

    override fun onChannelClick(channel: IChannel?) {
        val currentService = service
        if (channel == null || currentService == null || !currentService.isConnected) {
            return
        }
        (requireActivity() as AppCompatActivity).supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, ChannelDetailFragment.newInstance(channel.id))
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .addToBackStack(null)
            .commit()
    }

    override fun onUserClick(user: IUser?) {
        if (user == null) {
            return
        }
        (requireActivity() as? MumlaActivity)?.openUserProfile(user.session)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Settings.PREF_SHOW_USER_COUNT == key && channelListAdapter != null) {
            channelListAdapter?.setShowChannelUserCount(settings.shouldShowUserCount())
        }
    }

    companion object {
        private val TAG = ChannelListFragment::class.java.name
    }
}
