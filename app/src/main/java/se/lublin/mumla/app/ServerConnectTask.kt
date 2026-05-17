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

package se.lublin.mumla.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.AsyncTask
import androidx.core.content.ContextCompat
import se.lublin.humla.HumlaService
import se.lublin.humla.model.Server
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.db.MumlaDatabase
import se.lublin.mumla.service.MumlaService
import se.lublin.mumla.util.MumlaTrustStore

class ServerConnectTask(
    private val context: Context,
    private val database: MumlaDatabase,
) : AsyncTask<Server, Void, Intent>() {
    private val settings = Settings.getInstance(context)

    override fun doInBackground(vararg params: Server): Intent {
        val server = params[0]
        val inputMethod = settings.humlaInputMethod

        val audioSource = if (settings.isHandsetMode) {
            MediaRecorder.AudioSource.DEFAULT
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val audioStream = if (settings.isHandsetMode) {
            AudioManager.STREAM_VOICE_CALL
        } else {
            AudioManager.STREAM_MUSIC
        }

        val connectIntent = Intent(context, MumlaService::class.java)
        connectIntent.putExtra(HumlaService.EXTRAS_SERVER, server)
        connectIntent.putExtra(
            HumlaService.EXTRAS_CLIENT_NAME,
            "${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
        )
        connectIntent.putExtra(HumlaService.EXTRAS_TRANSMIT_MODE, inputMethod)
        connectIntent.putExtra(HumlaService.EXTRAS_DETECTION_THRESHOLD, settings.detectionThreshold)
        connectIntent.putExtra(HumlaService.EXTRAS_AMPLITUDE_BOOST, settings.amplitudeBoostMultiplier)
        connectIntent.putExtra(HumlaService.EXTRAS_AUTO_RECONNECT, settings.isAutoReconnectEnabled)
        connectIntent.putExtra(HumlaService.EXTRAS_AUTO_RECONNECT_DELAY, MumlaService.RECONNECT_DELAY)
        connectIntent.putExtra(HumlaService.EXTRAS_USE_OPUS, !settings.isOpusDisabled)
        connectIntent.putExtra(HumlaService.EXTRAS_INPUT_RATE, settings.inputSampleRate)
        connectIntent.putExtra(HumlaService.EXTRAS_INPUT_QUALITY, settings.inputQuality)
        connectIntent.putExtra(HumlaService.EXTRAS_FORCE_TCP, settings.isTcpForced)
        connectIntent.putExtra(HumlaService.EXTRAS_USE_TOR, settings.isTorEnabled)
        connectIntent.putStringArrayListExtra(
            HumlaService.EXTRAS_ACCESS_TOKENS,
            ArrayList(database.getAccessTokens(server.id)),
        )
        connectIntent.putExtra(HumlaService.EXTRAS_AUDIO_SOURCE, audioSource)
        connectIntent.putExtra(HumlaService.EXTRAS_AUDIO_STREAM, audioStream)
        connectIntent.putExtra(HumlaService.EXTRAS_FRAMES_PER_PACKET, settings.framesPerPacket)
        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE, MumlaTrustStore.getTrustStorePath(context))
        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE_PASSWORD, MumlaTrustStore.getTrustStorePassword())
        connectIntent.putExtra(HumlaService.EXTRAS_TRUST_STORE_FORMAT, MumlaTrustStore.getTrustStoreFormat())
        connectIntent.putExtra(HumlaService.EXTRAS_HALF_DUPLEX, settings.isHalfDuplex)
        connectIntent.putExtra(HumlaService.EXTRAS_ENABLE_PREPROCESSOR, settings.isPreprocessorEnabled)
        connectIntent.putExtra(HumlaService.EXTRAS_ECHO_CANCELLATION_METHOD, settings.echoCancellationMethod)
        if (server.isSaved) {
            connectIntent.putExtra(HumlaService.EXTRAS_LOCAL_MUTE_HISTORY, ArrayList(database.getLocalMutedUsers(server.id)))
            connectIntent.putExtra(HumlaService.EXTRAS_LOCAL_IGNORE_HISTORY, ArrayList(database.getLocalIgnoredUsers(server.id)))
        }

        if (settings.isUsingCertificate) {
            val certificateId = settings.defaultCertificate
            val certificate = database.getCertificateData(certificateId)
            if (certificate != null) {
                connectIntent.putExtra(HumlaService.EXTRAS_CERTIFICATE, certificate)
            }
        }

        connectIntent.action = HumlaService.ACTION_CONNECT
        return connectIntent
    }

    override fun onPostExecute(intent: Intent) {
        super.onPostExecute(intent)
        ContextCompat.startForegroundService(context, intent)
    }
}
