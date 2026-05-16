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
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver

class PermissionsPopupMenu(
    context: Context,
    anchor: View,
    menuRes: Int,
    private val prepareListener: IOnMenuPrepareListener,
    itemClickListener: PopupMenu.OnMenuItemClickListener,
    private val channel: IChannel,
    private val service: IHumlaService,
) : PopupMenu.OnDismissListener {
    private val menu = PopupMenu(context, anchor)

    private val permissionsObserver: IHumlaObserver = object : HumlaObserver() {
        override fun onChannelPermissionsUpdated(channel: IChannel?) {
            if (this@PermissionsPopupMenu.channel == channel) {
                prepareListener.onMenuPrepare(menu.menu, permissions)
            }
        }
    }

    private val permissions: Int
        get() = if (service.isConnected) {
            if (channel.id == 0) service.HumlaSession().permissions else channel.permissions
        } else {
            0
        }

    init {
        menu.inflate(menuRes)
        menu.setOnDismissListener(this)
        menu.setOnMenuItemClickListener(itemClickListener)
    }

    fun show() {
        service.registerObserver(permissionsObserver)
        prepareListener.onMenuPrepare(menu.menu, permissions)
        if (permissions == 0) {
            if (service.isConnected) {
                service.HumlaSession().requestPermissions(channel.id)
            }
        }
        menu.show()
    }

    fun dismiss() {
        menu.dismiss()
    }

    override fun onDismiss(popupMenu: PopupMenu) {
        service.unregisterObserver(permissionsObserver)
    }

    fun interface IOnMenuPrepareListener {
        fun onMenuPrepare(menu: Menu, permissions: Int)
    }
}
