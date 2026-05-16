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

package se.lublin.humla.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class BluetoothScoReceiver(
    context: Context,
    private val listener: Listener
) : BroadcastReceiver() {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var isBluetoothScoOn: Boolean = false
        private set

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                isBluetoothScoOn = true
                listener.onBluetoothScoConnected()
            }
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
            AudioManager.SCO_AUDIO_STATE_ERROR -> {
                audioManager.stopBluetoothSco()
                isBluetoothScoOn = false
                listener.onBluetoothScoDisconnected()
            }
        }
    }

    fun startBluetoothSco() {
        audioManager.startBluetoothSco()
    }

    fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
        isBluetoothScoOn = false
    }

    interface Listener {
        fun onBluetoothScoConnected()
        fun onBluetoothScoDisconnected()
    }
}
