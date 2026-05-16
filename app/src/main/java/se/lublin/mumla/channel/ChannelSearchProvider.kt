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

import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.IBinder
import android.util.Log
import java.util.Locale
import se.lublin.humla.IHumlaService
import se.lublin.humla.IHumlaSession
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.mumla.R
import se.lublin.mumla.service.MumlaService

class ChannelSearchProvider : ContentProvider() {
    private var service: IHumlaService? = null
    private val serviceLock = Object()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            this@ChannelSearchProvider.service = (service as MumlaService.MumlaBinder).service
            synchronized(serviceLock) {
                serviceLock.notify()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        if (service == null) {
            val serviceIntent = Intent(context, MumlaService::class.java)
            context.bindService(serviceIntent, connection, 0)

            synchronized(serviceLock) {
                try {
                    serviceLock.wait(5000)
                } catch (exception: InterruptedException) {
                    exception.printStackTrace()
                }

                if (service == null) {
                    Log.v(TAG, "Failed to connect to service from search provider!")
                    return null
                }
            }
        }

        val currentService = service ?: return null
        if (!currentService.isConnected) {
            return null
        }

        val session = currentService.HumlaSession()
        val query = selectionArgs.orEmpty()
            .joinToString(" ")
            .lowercase(Locale.getDefault())

        val cursor = MatrixCursor(
            arrayOf(
                "_ID",
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_ICON_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            ),
        )

        val channels = channelSearch(session.rootChannel, query)
        val users = userSearch(session.rootChannel, query)

        for (index in channels.indices) {
            val channel = channels[index]
            cursor.addRow(
                arrayOf<Any>(
                    index,
                    INTENT_DATA_CHANNEL,
                    channel.name,
                    R.drawable.ic_action_channels,
                    context.resources.getQuantityString(
                        R.plurals.search_channel_users,
                        channel.subchannelUserCount,
                        channel.subchannelUserCount,
                    ),
                    channel.id,
                ),
            )
        }

        for (index in users.indices) {
            val user = users[index]
            cursor.addRow(
                arrayOf<Any>(
                    index,
                    INTENT_DATA_USER,
                    user.name,
                    R.drawable.ic_action_user_dark,
                    context.getString(R.string.user),
                    user.session,
                ),
            )
        }
        return cursor
    }

    private fun userSearch(root: IChannel?, str: String): List<IUser> {
        val users = mutableListOf<IUser>()
        userSearch(root, str, users)
        return users
    }

    private fun userSearch(root: IChannel?, str: String, users: MutableList<IUser>) {
        if (root == null) {
            return
        }
        for (user in root.users) {
            if (user != null && user.name != null && user.name.lowercase().contains(str.lowercase())) {
                users.add(user)
            }
        }
        for (subchannel in root.subchannels) {
            if (subchannel != null) {
                userSearch(subchannel, str, users)
            }
        }
    }

    private fun channelSearch(root: IChannel?, str: String): List<IChannel> {
        val channels = mutableListOf<IChannel>()
        channelSearch(root, str, channels)
        return channels
    }

    private fun channelSearch(root: IChannel?, str: String, channels: MutableList<IChannel>) {
        if (root == null) {
            return
        }

        if (root.name.lowercase().contains(str.lowercase())) {
            channels.add(root)
        }

        for (subchannel in root.subchannels) {
            if (subchannel != null) {
                channelSearch(subchannel, str, channels)
            }
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private val TAG = ChannelSearchProvider::class.java.name
        const val INTENT_DATA_CHANNEL = "channel"
        const val INTENT_DATA_USER = "user"
    }
}
