/*
 * Copyright (C) 2015 Andrew Comminos
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import se.lublin.mumla.R

class MumlaReconnectNotification(
    private val context: Context,
    private val listener: OnActionListener,
) {
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BROADCAST_DISMISS -> listener.onReconnectNotificationDismissed()
                BROADCAST_RECONNECT -> listener.reconnect()
                BROADCAST_CANCEL_RECONNECT -> listener.cancelReconnect()
            }
        }
    }

    fun show(error: String, autoReconnect: Boolean) {
        val filter = IntentFilter().apply {
            addAction(BROADCAST_DISMISS)
            addAction(BROADCAST_RECONNECT)
            addAction(BROADCAST_CANCEL_RECONNECT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(notificationReceiver, filter)
            }
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }

        var channelId = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = "reconnecting_channel"
            val channel = NotificationChannel(
                channelId,
                "Reconnecting",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, channelId)

        builder.setSmallIcon(R.drawable.ic_stat_notify)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setContentTitle(context.getString(R.string.mumlaDisconnected))
            .setContentText(error)

        val dismissIntent = Intent(BROADCAST_DISMISS).setPackage(context.packageName)
        builder.setDeleteIntent(
            PendingIntent.getBroadcast(
                context,
                2,
                dismissIntent,
                FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE,
            ),
        )

        if (autoReconnect) {
            val cancelIntent = Intent(BROADCAST_CANCEL_RECONNECT).setPackage(context.packageName)
            builder.addAction(
                R.drawable.ic_action_delete_dark,
                context.getString(R.string.cancel_reconnect),
                PendingIntent.getBroadcast(context, 2, cancelIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE),
            )
            builder.setOngoing(true)
        } else {
            val reconnectIntent = Intent(BROADCAST_RECONNECT).setPackage(context.packageName)
            builder.addAction(
                R.drawable.ic_action_move,
                context.getString(R.string.reconnect),
                PendingIntent.getBroadcast(context, 2, reconnectIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE),
            )
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun hide() {
        try {
            context.unregisterReceiver(notificationReceiver)
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    interface OnActionListener {
        fun onReconnectNotificationDismissed()
        fun reconnect()
        fun cancelReconnect()
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val BROADCAST_DISMISS = "b_dismiss"
        private const val BROADCAST_RECONNECT = "b_reconnect"
        private const val BROADCAST_CANCEL_RECONNECT = "b_cancel_reconnect"

        @JvmStatic
        fun show(
            context: Context,
            error: String,
            autoReconnect: Boolean,
            listener: OnActionListener,
        ): MumlaReconnectNotification {
            val notification = MumlaReconnectNotification(context, listener)
            notification.show(error, autoReconnect)
            return notification
        }
    }
}
