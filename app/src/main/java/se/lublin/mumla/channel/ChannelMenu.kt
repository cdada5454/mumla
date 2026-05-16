/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.WhisperTargetChannel
import se.lublin.humla.net.Permissions
import se.lublin.humla.util.VoiceTargetMode
import se.lublin.mumla.R
import se.lublin.mumla.channel.comment.ChannelDescriptionFragment
import se.lublin.mumla.db.MumlaDatabase

class ChannelMenu(
    private val context: Context,
    private val channel: IChannel,
    private val service: IHumlaService,
    private val database: MumlaDatabase,
    private val fragmentManager: FragmentManager,
) : PermissionsPopupMenu.IOnMenuPrepareListener, PopupMenu.OnMenuItemClickListener {
    override fun onMenuPrepare(menu: Menu, permissions: Int) {
        menu.findItem(R.id.context_channel_add).isVisible =
            permissions and (Permissions.MakeChannel or Permissions.MakeTempChannel) > 0 || isSuperUser()
        menu.findItem(R.id.context_channel_edit).isVisible = permissions and Permissions.Write > 0
        menu.findItem(R.id.context_channel_remove).isVisible = permissions and Permissions.Write > 0
        menu.findItem(R.id.context_channel_view_description).isVisible =
            channel.description != null || channel.descriptionHash != null
        val server = service.targetServer
        menu.findItem(R.id.context_channel_pin).isChecked = database.isChannelPinned(server.id, channel.id)
        if (service.isConnected) {
            val ourChannel = try {
                service.HumlaSession().sessionChannel
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "exception in onMenuPrepare: $exception")
                null
            }
            if (ourChannel != null) {
                menu.findItem(R.id.context_channel_link).isChecked = channel.links.contains(ourChannel)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (!service.isConnected) {
            return false
        }

        when (item.itemId) {
            R.id.context_channel_join -> service.HumlaSession().joinChannel(channel.id)
            R.id.context_channel_talk -> setTalkTarget()
            R.id.context_channel_add, R.id.context_channel_edit -> showChannelEdit(item.itemId)
            R.id.context_channel_remove -> confirmRemoveChannel()
            R.id.context_channel_view_description -> showDescription()
            R.id.context_channel_pin -> togglePinned()
            R.id.context_channel_link -> toggleLinked(item)
            R.id.context_channel_unlink_all -> service.HumlaSession().unlinkAllChannels(channel)
            R.id.context_channel_shout -> showShoutDialog()
            else -> return false
        }
        return true
    }

    private fun setTalkTarget() {
        val session = service.HumlaSession()
        if (session.voiceTargetMode == VoiceTargetMode.WHISPER) {
            session.unregisterWhisperTarget(session.voiceTargetId)
        }
        val channelTarget = WhisperTargetChannel(channel, false, false, null)
        val id = session.registerWhisperTarget(channelTarget)
        if (id > 0) {
            session.voiceTargetId = id
        } else {
            Toast.makeText(context, R.string.shout_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showChannelEdit(itemId: Int) {
        val args = Bundle()
        if (itemId == R.id.context_channel_add) {
            args.putInt("parent", channel.id)
            args.putBoolean("adding", true)
        } else {
            args.putInt("channel", channel.id)
            args.putBoolean("adding", false)
        }
        ChannelEditFragment().apply { arguments = args }
            .show(fragmentManager, "ChannelAdd")
    }

    private fun confirmRemoveChannel() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.confirm)
            .setMessage(R.string.confirm_delete_channel)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (service.isConnected) {
                    service.HumlaSession().removeChannel(channel.id)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isSuperUser(): Boolean =
        service.isConnected && service.HumlaSession().sessionUser.name == "SuperUser"

    private fun showDescription() {
        val commentArgs = Bundle()
        commentArgs.putInt("channel", channel.id)
        commentArgs.putString("comment", channel.description)
        commentArgs.putBoolean("editing", false)
        val commentFragment = ChannelDescriptionFragment().apply {
            arguments = commentArgs
        } as DialogFragment
        commentFragment.show(fragmentManager, ChannelDescriptionFragment::class.java.name)
    }

    private fun togglePinned() {
        val serverId = service.targetServer.id
        val pinned = database.isChannelPinned(serverId, channel.id)
        if (!pinned) {
            database.addPinnedChannel(serverId, channel.id)
        } else {
            database.removePinnedChannel(serverId, channel.id)
        }
    }

    private fun toggleLinked(item: MenuItem) {
        val sessionChannel = service.HumlaSession().sessionChannel
        if (!item.isChecked) {
            service.HumlaSession().linkChannels(sessionChannel, channel)
        } else {
            service.HumlaSession().unlinkChannels(sessionChannel, channel)
        }
    }

    private fun showShoutDialog() {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val subchannelBox = CheckBox(context)
        subchannelBox.setText(R.string.shout_include_subchannels)
        layout.addView(subchannelBox)
        val linkedBox = CheckBox(context)
        linkedBox.setText(R.string.shout_include_linked)
        layout.addView(linkedBox)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shout_configure)
            .setView(layout)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (!service.isConnected) {
                    return@setPositiveButton
                }
                val session = service.HumlaSession()
                if (session.voiceTargetMode == VoiceTargetMode.WHISPER) {
                    session.unregisterWhisperTarget(session.voiceTargetId)
                }
                val channelTarget = WhisperTargetChannel(channel, linkedBox.isChecked, subchannelBox.isChecked, null)
                val id = session.registerWhisperTarget(channelTarget)
                if (id > 0) {
                    session.voiceTargetId = id
                } else {
                    Toast.makeText(context, R.string.shout_failed, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showPopup(anchor: View) {
        PermissionsPopupMenu(
            context,
            anchor,
            R.menu.context_channel,
            this,
            this,
            channel,
            service,
        ).show()
    }

    companion object {
        private val TAG = ChannelMenu::class.java.name
    }
}
