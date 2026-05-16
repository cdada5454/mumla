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

package se.lublin.mumla.channel

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.RemoteException
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import java.util.LinkedList
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.model.TalkState
import se.lublin.humla.model.WhisperTargetChannel
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.VoiceTargetMode
import se.lublin.mumla.R
import se.lublin.mumla.db.MumlaDatabase
import se.lublin.mumla.drawable.CircleDrawable
import se.lublin.mumla.service.MumlaService

class ChannelListAdapter(
    private val context: Context,
    private var service: IHumlaService?,
    private val database: MumlaDatabase,
    private val fragmentManager: FragmentManager,
    showPinnedOnly: Boolean,
    private var showChannelUserCountValue: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), UserMenu.IUserLocalStateListener {
    private val rootChannels: List<Int>
    private val nodes: MutableList<Node> = LinkedList()
    private val expandedChannelIds: MutableSet<Int> = mutableSetOf()
    private var userClickListener: OnUserClickListener? = null
    private var channelClickListener: OnChannelClickListener? = null

    init {
        setHasStableIds(true)
        rootChannels = if (showPinnedOnly) {
            database.getPinnedChannels(service!!.targetServer.id)
        } else {
            listOf(0)
        }
        if (showPinnedOnly) {
            expandedChannelIds.addAll(rootChannels)
        }
        updateChannels()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CHANNEL -> ChannelViewHolder(ChannelListLayout.createChannelRow(context))
            VIEW_TYPE_USER -> UserViewHolder(ChannelListLayout.createUserRow(context))
            else -> throw IllegalArgumentException("Unknown channel list view type $viewType")
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val node = nodes[position]
        if (node.isChannel) {
            bindChannel(viewHolder as ChannelViewHolder, node, node.channel!!)
        } else if (node.isUser) {
            bindUser(viewHolder as UserViewHolder, node, node.user!!)
        }
    }

    private fun bindChannel(holder: ChannelViewHolder, node: Node, channel: IChannel) {
        holder.itemView.setOnClickListener {
            channelClickListener?.onChannelClick(channel)
        }
        holder.channelName.text = channel.name

        var nameTypeface = Typeface.NORMAL
        val currentService = service
        if (currentService != null && currentService.isConnected) {
            val session = currentService.HumlaSession()
            val ourChannel = try {
                session.sessionChannel
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "exception in onBindViewHolder: $exception")
                null
            }
            if (ourChannel != null) {
                if (channel == ourChannel) {
                    nameTypeface = nameTypeface or Typeface.BOLD
                    if (channel.links.isNotEmpty()) {
                        nameTypeface = nameTypeface or Typeface.ITALIC
                    }
                }
                if (channel.links.contains(ourChannel)) {
                    nameTypeface = nameTypeface or Typeface.ITALIC
                }
            }
        }
        holder.channelName.setTypeface(null, nameTypeface)

        if (showChannelUserCountValue) {
            holder.channelUserCount.visibility = View.VISIBLE
            holder.channelUserCount.text = String.format("%d", channel.subchannelUserCount)
        } else {
            holder.channelUserCount.visibility = View.GONE
        }

        val metrics = context.resources.displayMetrics
        val margin = node.depth * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25f, metrics)
        holder.channelHolder.setPadding(
            margin.toInt(),
            holder.channelHolder.paddingTop,
            holder.channelHolder.paddingRight,
            holder.channelHolder.paddingBottom,
        )

        val hasSubchannels = channel.subchannels.isNotEmpty()
        val isExpanded = expandedChannelIds.contains(channel.id)
        holder.expandButton.visibility = if (hasSubchannels) View.VISIBLE else View.INVISIBLE
        holder.expandButton.setText(
            if (isExpanded) R.string.collapse else R.string.expand
        )
        holder.expandButton.setOnClickListener {
            if (expandedChannelIds.contains(channel.id)) {
                expandedChannelIds.remove(channel.id)
            } else {
                expandedChannelIds.add(channel.id)
            }
            updateChannels()
            notifyDataSetChanged()
        }
        holder.channelTalking.visibility = if (channel.id == getPushToTalkTargetChannelId()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        holder.moreButton.setOnClickListener { view ->
            ChannelMenu(context, channel, service!!, database, fragmentManager).showPopup(view)
        }
        holder.itemView.setOnLongClickListener {
            holder.moreButton.performClick()
            true
        }
    }

    private fun bindUser(holder: UserViewHolder, node: Node, user: IUser) {
        holder.itemView.setOnClickListener {
            userClickListener?.onUserClick(user)
        }
        holder.userName.text = user.name

        var selfSession = -1
        try {
            val currentService = service
            if (currentService != null) {
                selfSession = currentService.HumlaSession().sessionId
            }
        } catch (exception: HumlaDisconnectedException) {
            Log.d(TAG, "exception in onBindViewHolder: $exception")
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "exception in onBindViewHolder: $exception")
        }

        val typefaceStyle = if (service != null && service!!.isConnected && user.session == selfSession) {
            Typeface.BOLD
        } else {
            Typeface.NORMAL
        }
        holder.userName.setTypeface(null, typefaceStyle)
        holder.userTalkHighlight.setImageDrawable(getTalkStateDrawable(user))

        val metrics = context.resources.displayMetrics
        val margin = (node.depth + 1) * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25f, metrics)
        holder.userHolder.setPadding(
            margin.toInt(),
            holder.userHolder.paddingTop,
            holder.userHolder.paddingRight,
            holder.userHolder.paddingBottom,
        )

        holder.moreButton.setOnClickListener { view ->
            UserMenu(context, user, service as MumlaService, fragmentManager, this).showPopup(view)
        }
        holder.itemView.setOnLongClickListener {
            holder.moreButton.performClick()
            true
        }
    }

    override fun getItemCount(): Int = nodes.size

    override fun getItemViewType(position: Int): Int {
        val node = nodes[position]
        return when {
            node.isChannel -> VIEW_TYPE_CHANNEL
            node.isUser -> VIEW_TYPE_USER
            else -> 0
        }
    }

    override fun getItemId(position: Int): Long {
        return try {
            nodes[position].id
        } catch (exception: RemoteException) {
            exception.printStackTrace()
            -1
        }
    }

    fun updateChannels() {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return
        }

        val session = currentService.HumlaSession()
        nodes.clear()
        try {
            for (channelId in rootChannels) {
                val channel = session.getChannel(channelId)
                if (channel != null) {
                    constructNodes(null, channel, 0, nodes)
                }
            }
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "exception in updateChannels: $exception")
        }
    }

    fun updateUserStates(user: IUser?, view: RecyclerView?) {
        if (user == null || view == null) {
            return
        }
        val itemId = user.session.toLong() or USER_ID_MASK
        val holder = view.findViewHolderForItemId(itemId) as? UserViewHolder
        if (holder != null) {
            val newState = getTalkStateDrawable(user)
            val state = holder.userTalkHighlight.drawable.current.constantState
            if (state != null && state != newState.constantState) {
                holder.userTalkHighlight.setImageDrawable(newState)
            }
        }
    }

    private fun getTalkStateDrawable(user: IUser): Drawable {
        val resources: Resources = context.resources
        val texture = user.texture
        return when {
            user.isSelfDeafened -> resources.getDrawable(R.drawable.outline_circle_deafened)
            user.isDeafened -> resources.getDrawable(R.drawable.outline_circle_server_deafened)
            user.isSelfMuted -> resources.getDrawable(R.drawable.outline_circle_muted)
            user.isMuted -> resources.getDrawable(R.drawable.outline_circle_server_muted)
            user.isSuppressed -> resources.getDrawable(R.drawable.outline_circle_suppressed)
            user.talkState == TalkState.TALKING ||
                user.talkState == TalkState.SHOUTING ||
                user.talkState == TalkState.WHISPERING -> resources.getDrawable(R.drawable.outline_circle_talking_on)
            texture != null -> {
                val bitmap = BitmapFactory.decodeByteArray(texture, 0, texture.size)
                if (bitmap != null) {
                    CircleDrawable(context.resources, bitmap)
                } else {
                    resources.getDrawable(R.drawable.outline_circle_talking_off)
                }
            }
            else -> resources.getDrawable(R.drawable.outline_circle_talking_off)
        }
    }

    private fun getPushToTalkTargetChannelId(): Int? {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return null
        }
        return try {
            val session = currentService.HumlaSession()
            if (session.voiceTargetMode == VoiceTargetMode.WHISPER) {
                (session.whisperTarget as? WhisperTargetChannel)?.channel?.id
            } else {
                session.sessionChannel.id
            }
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "exception in getPushToTalkTargetChannelId: $exception")
            null
        }
    }

    fun getUserPosition(session: Int): Int {
        val itemId = session.toLong() or USER_ID_MASK
        for (index in nodes.indices) {
            try {
                if (nodes[index].id == itemId) {
                    return index
                }
            } catch (exception: RemoteException) {
                exception.printStackTrace()
            }
        }
        return -1
    }

    fun getChannelPosition(channelId: Int): Int {
        val itemId = channelId.toLong() or CHANNEL_ID_MASK
        for (index in nodes.indices) {
            try {
                if (nodes[index].id == itemId) {
                    return index
                }
            } catch (exception: RemoteException) {
                exception.printStackTrace()
            }
        }
        return -1
    }

    fun setOnUserClickListener(listener: OnUserClickListener?) {
        userClickListener = listener
    }

    fun setOnChannelClickListener(listener: OnChannelClickListener?) {
        channelClickListener = listener
    }

    fun setShowChannelUserCount(showUserCount: Boolean) {
        showChannelUserCountValue = showUserCount
        notifyDataSetChanged()
    }

    private fun constructNodes(parent: Node?, channel: IChannel, depth: Int, nodes: MutableList<Node>) {
        val channelNode = Node(parent, depth, channel = channel)
        nodes.add(channelNode)

        if (expandedChannelIds.contains(channel.id)) {
            for (subchannel in channel.subchannels) {
                constructNodes(channelNode, subchannel, depth + 1, nodes)
            }
        }
    }

    fun setService(service: IHumlaService) {
        this.service = service
        if (service.connectionState == HumlaService.ConnectionState.CONNECTED) {
            updateChannels()
            notifyDataSetChanged()
        }
    }

    override fun onLocalUserStateUpdated(user: IUser) {
        notifyDataSetChanged()

        val server = service!!.targetServer
        if (user.userId >= 0 && server.isSaved) {
            Thread {
                if (user.isLocalMuted) {
                    database.addLocalMutedUser(server.id, user.userId)
                } else {
                    database.removeLocalMutedUser(server.id, user.userId)
                }
                if (user.isLocalIgnored) {
                    database.addLocalIgnoredUser(server.id, user.userId)
                } else {
                    database.removeLocalIgnoredUser(server.id, user.userId)
                }
            }.start()
        }
    }

    private class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userHolder: LinearLayout = itemView.findViewById(R.id.user_row_title)
        val userName: TextView = itemView.findViewById(R.id.user_row_name)
        val userTalkHighlight: ImageView = itemView.findViewById(R.id.user_row_talk_highlight)
        val moreButton: ImageView = itemView.findViewById(R.id.user_row_more)
    }

    private class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val channelHolder: LinearLayout = itemView.findViewById(R.id.channel_row_title)
        val channelName: TextView = itemView.findViewById(R.id.channel_row_name)
        val channelUserCount: TextView = itemView.findViewById(R.id.channel_row_count)
        val expandButton: TextView = itemView.findViewById(R.id.channel_row_join)
        val channelTalking: ImageView = itemView.findViewById(R.id.channel_row_talking)
        val moreButton: ImageView = itemView.findViewById(R.id.channel_row_more)
    }

    private class Node(
        val parent: Node?,
        val depth: Int,
        val channel: IChannel? = null,
        val user: IUser? = null,
    ) {
        val isChannel: Boolean
            get() = channel != null

        val isUser: Boolean
            get() = user != null

        val id: Long
            @Throws(RemoteException::class)
            get() = when {
                isChannel -> CHANNEL_ID_MASK or channel!!.id.toLong()
                isUser -> USER_ID_MASK or user!!.session.toLong()
                else -> 0
            }
    }

    companion object {
        private val TAG = ChannelListAdapter::class.java.name

        const val CHANNEL_ID_MASK = 0x1L shl 32
        const val USER_ID_MASK = 0x1L shl 33

        private const val VIEW_TYPE_CHANNEL = 1
        private const val VIEW_TYPE_USER = 2
    }
}
