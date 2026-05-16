/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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

package se.lublin.mumla.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import se.lublin.humla.model.IMessage
import se.lublin.mumla.R
import se.lublin.mumla.app.DrawerAdapter
import se.lublin.mumla.app.MumlaActivity

class MumlaMessageNotification(private val context: Context) {
    private val unreadMessages = mutableListOf<IMessage>()

    fun show(message: IMessage) {
        unreadMessages.add(message)

        val style = NotificationCompat.InboxStyle()
        style.setBigContentTitle(
            context.resources.getQuantityString(
                R.plurals.notification_unread_many,
                unreadMessages.size,
                unreadMessages.size,
            ),
        )
        for (unreadMessage in unreadMessages) {
            val line = context.getString(
                R.string.notification_message,
                unreadMessage.actorName,
                unreadMessage.message,
            )
            style.addLine(line)
        }

        val channelListIntent = Intent(context, MumlaActivity::class.java)
        channelListIntent.putExtra(MumlaActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            channelListIntent,
            FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE,
        )

        var channelId = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = "message_channel"
            val channelName = context.getString(R.string.messageReceived)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, channelId)

        builder.setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setContentTitle(message.actorName)
            .setContentText(message.message)
            .setVibrate(VIBRATION_PATTERN)
            .setStyle(style)

        if (unreadMessages.isNotEmpty()) {
            builder.setNumber(unreadMessages.size)
        }

        val manager = NotificationManagerCompat.from(context)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismiss() {
        unreadMessages.clear()
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private val VIBRATION_PATTERN = longArrayOf(0, 100)
    }
}
