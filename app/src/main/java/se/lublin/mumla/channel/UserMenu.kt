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
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.net.Permissions
import se.lublin.mumla.R
import se.lublin.mumla.channel.comment.UserCommentFragment
import se.lublin.mumla.service.MumlaService
import se.lublin.mumla.util.ModelUtils

class UserMenu(
    private val context: Context,
    private val user: IUser,
    private val service: MumlaService,
    private val fragmentManager: FragmentManager,
    private val stateListener: IUserLocalStateListener,
) : PermissionsPopupMenu.IOnMenuPrepareListener, PopupMenu.OnMenuItemClickListener {
    override fun onMenuPrepare(menu: Menu, permissions: Int) {
        val self = try {
            user.session == service.sessionId
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "exception in onMenuPrepare: $exception")
            return
        }
        val perms = service.permissions
        val channel = user.channel
        if (channel == null) {
            Log.d(TAG, "mUser.getChannel()==null in onMenuPrepare")
            return
        }
        val channelPerms = if (channel.id != 0) channel.permissions else perms

        menu.findItem(R.id.context_kick).isVisible =
            !self && perms and (Permissions.Kick or Permissions.Ban or Permissions.Write) > 0
        menu.findItem(R.id.context_ban).isVisible =
            !self && perms and (Permissions.Ban or Permissions.Write) > 0
        menu.findItem(R.id.context_mute).isVisible =
            channelPerms and (Permissions.Write or Permissions.MuteDeafen) > 0 &&
                (!self || user.isMuted || user.isSuppressed)
        menu.findItem(R.id.context_deafen).isVisible =
            channelPerms and (Permissions.Write or Permissions.MuteDeafen) > 0 &&
                (!self || user.isDeafened)
        menu.findItem(R.id.context_priority).isVisible =
            channelPerms and (Permissions.Write or Permissions.MuteDeafen) > 0
        menu.findItem(R.id.context_move).isVisible = !self && perms and Permissions.Move > 0
        menu.findItem(R.id.context_change_comment).isVisible = self
        val comment = user.comment
        val userHash = user.hash
        menu.findItem(R.id.context_reset_comment).isVisible =
            !self && ((comment != null && comment.isNotEmpty()) || user.commentHash != null) &&
                perms and (Permissions.Move or Permissions.Write) > 0
        menu.findItem(R.id.context_view_comment).isVisible =
            (comment != null && comment.isNotEmpty()) || user.commentHash != null
        menu.findItem(R.id.context_register).isVisible =
            user.userId < 0 &&
                (userHash != null && userHash.isNotEmpty()) &&
                perms and ((if (self) Permissions.SelfRegister else Permissions.Register) or Permissions.Write) > 0
        menu.findItem(R.id.context_local_mute).isVisible = !self
        menu.findItem(R.id.context_ignore_messages).isVisible = !self
        menu.findItem(R.id.context_private_call).isVisible = !self

        menu.findItem(R.id.context_mute).isChecked = user.isMuted || user.isSuppressed
        menu.findItem(R.id.context_deafen).isChecked = user.isDeafened
        menu.findItem(R.id.context_priority).isChecked = user.isPrioritySpeaker
        menu.findItem(R.id.context_local_mute).isChecked = user.isLocalMuted
        menu.findItem(R.id.context_ignore_messages).isChecked = user.isLocalIgnored
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.context_ban, R.id.context_kick -> showKickBanDialog(menuItem)
            R.id.context_mute -> service.setMuteDeafState(
                user.session,
                !(user.isMuted || user.isSuppressed),
                user.isDeafened,
            )
            R.id.context_deafen -> service.setMuteDeafState(user.session, user.isMuted, !user.isDeafened)
            R.id.context_move -> showChannelMoveDialog()
            R.id.context_priority -> service.setPrioritySpeaker(user.session, !user.isPrioritySpeaker)
            R.id.context_local_mute -> {
                user.isLocalMuted = !user.isLocalMuted
                stateListener.onLocalUserStateUpdated(user)
            }
            R.id.context_ignore_messages -> {
                user.isLocalIgnored = !user.isLocalIgnored
                stateListener.onLocalUserStateUpdated(user)
            }
            R.id.context_change_comment -> showUserComment(true)
            R.id.context_view_comment -> showUserComment(false)
            R.id.context_reset_comment -> confirmResetComment()
            R.id.context_register -> service.registerUser(user.session)
            R.id.context_private_call -> service.startPrivateCall(user.session)
            else -> return false
        }
        return true
    }

    private fun showKickBanDialog(menuItem: MenuItem) {
        val reasonField = EditText(context)
        reasonField.setHint(R.string.hint_reason)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.user_menu_kick)
            .setView(reasonField)
            .setPositiveButton(R.string.user_menu_kick) { _, _ ->
                service.kickBanUser(
                    user.session,
                    reasonField.text.toString(),
                    menuItem.itemId == R.id.context_ban,
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmResetComment() {
        MaterialAlertDialogBuilder(context)
            .setMessage(context.getString(R.string.confirm_reset_comment, user.name))
            .setPositiveButton(R.string.confirm) { _, _ ->
                service.setUserComment(user.session, "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUserComment(edit: Boolean) {
        val args = Bundle()
        args.putInt("session", user.session)
        args.putString("comment", user.comment)
        args.putBoolean("editing", edit)
        val fragment = UserCommentFragment().apply {
            arguments = args
        }
        fragment.show(fragmentManager, UserCommentFragment::class.java.name)
    }

    private fun showChannelMoveDialog() {
        val channels: List<IChannel> = ModelUtils.getChannelList(service.rootChannel)
        val channelNames = channels.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.user_menu_move)
            .setItems(channelNames) { _, which ->
                val channel = channels[which]
                service.moveUserToChannel(user.session, channel.id)
            }
            .show()
    }

    fun showPopup(anchor: View) {
        PermissionsPopupMenu(
            context,
            anchor,
            R.menu.context_user,
            this,
            this,
            user.channel ?: return,
            service,
        ).show()
    }

    interface IUserLocalStateListener {
        fun onLocalUserStateUpdated(user: IUser)
    }

    companion object {
        private val TAG = UserMenu::class.java.name
    }
}
