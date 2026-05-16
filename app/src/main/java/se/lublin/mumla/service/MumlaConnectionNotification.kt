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

package se.lublin.mumla.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import androidx.core.app.NotificationCompat
import se.lublin.mumla.R
import se.lublin.mumla.app.DrawerAdapter
import se.lublin.mumla.app.MumlaActivity

class MumlaConnectionNotification private constructor(
    private val service: Service,
    private var customContentText: String,
    private val listener: OnActionListener,
) {
    private var actionsShown = false

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BROADCAST_MUTE -> listener.onMuteToggled()
                BROADCAST_DEAFEN -> listener.onDeafenToggled()
                BROADCAST_OVERLAY -> listener.onOverlayToggled()
            }
        }
    }

    fun setCustomContentText(text: String) {
        customContentText = text
    }

    fun setActionsShown(actionsShown: Boolean) {
        this.actionsShown = actionsShown
    }

    fun show() {
        createNotification()

        val filter = IntentFilter().apply {
            addAction(BROADCAST_DEAFEN)
            addAction(BROADCAST_MUTE)
            addAction(BROADCAST_OVERLAY)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                service.registerReceiver(notificationReceiver, filter)
            }
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }
    }

    fun hide() {
        try {
            service.unregisterReceiver(notificationReceiver)
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }
        service.stopForeground(true)
    }

    private fun createNotification(): Notification {
        var channelId = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = "connected_channel"
            val channelName = service.getString(R.string.connected)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(service, channelId)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            builder.setContentTitle(service.getString(R.string.app_name))
        }
        builder.setContentText(customContentText)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setShowWhen(false)
            .setOngoing(true)

        if (actionsShown) {
            val muteIntent = Intent(BROADCAST_MUTE).setPackage(service.packageName)
            val deafenIntent = Intent(BROADCAST_DEAFEN).setPackage(service.packageName)
            val overlayIntent = Intent(BROADCAST_OVERLAY).setPackage(service.packageName)

            builder.addAction(
                R.drawable.ic_action_microphone,
                service.getString(R.string.mute),
                PendingIntent.getBroadcast(service, 1, muteIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE),
            )
            builder.addAction(
                R.drawable.ic_action_audio,
                service.getString(R.string.deafen),
                PendingIntent.getBroadcast(service, 1, deafenIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE),
            )
            builder.addAction(
                R.drawable.ic_action_channels,
                service.getString(R.string.overlay),
                PendingIntent.getBroadcast(service, 2, overlayIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE),
            )
        }

        val channelListIntent = Intent(service, MumlaActivity::class.java)
        channelListIntent.putExtra(MumlaActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER)
        val pendingIntent = PendingIntent.getActivity(
            service,
            0,
            channelListIntent,
            FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE,
        )
        builder.setContentIntent(pendingIntent)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
        return notification
    }

    interface OnActionListener {
        fun onMuteToggled()
        fun onDeafenToggled()
        fun onOverlayToggled()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val BROADCAST_MUTE = "b_mute"
        private const val BROADCAST_DEAFEN = "b_deafen"
        private const val BROADCAST_OVERLAY = "b_overlay"

        @JvmStatic
        fun create(service: Service, contentText: String, listener: OnActionListener): MumlaConnectionNotification =
            MumlaConnectionNotification(service, contentText, listener)
    }
}
