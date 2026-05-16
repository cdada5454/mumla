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

package se.lublin.mumla

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.preference.PreferenceManager
import se.lublin.humla.Constants

class Settings private constructor(context: Context) {
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val inputMethod: String
        get() {
        var method = preferences.getString(PREF_INPUT_METHOD, ARRAY_INPUT_METHOD_VOICE)
        if (!ARRAY_INPUT_METHODS.contains(method)) {
            method = ARRAY_INPUT_METHOD_VOICE
        }
        return method!!
    }

    val humlaInputMethod: Int
        get() {
        val inputMethod = inputMethod
        return when (inputMethod) {
            ARRAY_INPUT_METHOD_VOICE -> Constants.TRANSMIT_VOICE_ACTIVITY
            ARRAY_INPUT_METHOD_PTT -> Constants.TRANSMIT_PUSH_TO_TALK
            ARRAY_INPUT_METHOD_CONTINUOUS -> Constants.TRANSMIT_CONTINUOUS
            else -> throw RuntimeException("Could not convert input method '$inputMethod' to a Humla input method id!")
        }
    }

    fun setInputMethod(inputMethod: String) {
        if (inputMethod == ARRAY_INPUT_METHOD_VOICE ||
            inputMethod == ARRAY_INPUT_METHOD_PTT ||
            inputMethod == ARRAY_INPUT_METHOD_CONTINUOUS
        ) {
            preferences.edit().putString(PREF_INPUT_METHOD, inputMethod).apply()
        } else {
            throw RuntimeException("Invalid input method $inputMethod")
        }
    }

    val inputSampleRate: Int
        get() = preferences.getString(PREF_INPUT_RATE, DEFAULT_RATE)!!.toInt()

    val inputQuality: Int
        get() = preferences.getInt(PREF_INPUT_QUALITY, DEFAULT_INPUT_QUALITY)

    val amplitudeBoostMultiplier: Float
        get() = preferences.getInt(PREF_AMPLITUDE_BOOST, DEFAULT_AMPLITUDE_BOOST).toFloat() / 100

    val detectionThreshold: Float
        get() = preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD).toFloat() / 100

    val pushToTalkKey: Int
        get() = preferences.getInt(PREF_PUSH_KEY, DEFAULT_PUSH_KEY)

    val hotCorner: String
        get() = preferences.getString(PREF_HOT_CORNER_KEY, DEFAULT_HOT_CORNER)!!

    val isHotCornerEnabled: Boolean
        get() = ARRAY_HOT_CORNER_NONE != preferences.getString(PREF_HOT_CORNER_KEY, DEFAULT_HOT_CORNER)

    val hotCornerGravity: Int
        get() = when (hotCorner) {
            ARRAY_HOT_CORNER_BOTTOM_LEFT -> Gravity.LEFT or Gravity.BOTTOM
            ARRAY_HOT_CORNER_BOTTOM_RIGHT -> Gravity.RIGHT or Gravity.BOTTOM
            ARRAY_HOT_CORNER_TOP_LEFT -> Gravity.LEFT or Gravity.TOP
            ARRAY_HOT_CORNER_TOP_RIGHT -> Gravity.RIGHT or Gravity.TOP
            else -> 0
        }

    val pTTButtonHeight: Int
        get() = preferences.getInt(PREF_PTT_BUTTON_HEIGHT, DEFAULT_PTT_BUTTON_HEIGHT)

    val defaultCertificate: Long
        get() = preferences.getLong(PREF_CERT_ID, -1)

    val defaultUsername: String
        get() = preferences.getString(PREF_DEFAULT_USERNAME, DEFAULT_DEFAULT_USERNAME)!!

    val isPushToTalkToggle: Boolean
        get() = preferences.getBoolean(PREF_PTT_TOGGLE, DEFAULT_PTT_TOGGLE)

    val isPushToTalkButtonShown: Boolean
        get() = !preferences.getBoolean(PREF_PUSH_BUTTON_HIDE_KEY, DEFAULT_PUSH_BUTTON_HIDE)

    val isChatNotifyEnabled: Boolean
        get() = preferences.getBoolean(PREF_CHAT_NOTIFY, DEFAULT_CHAT_NOTIFY)

    val isTextToSpeechEnabled: Boolean
        get() = preferences.getBoolean(PREF_USE_TTS, DEFAULT_USE_TTS)

    val isShortTextToSpeechMessagesEnabled: Boolean
        get() = preferences.getBoolean(PREF_SHORT_TTS_MESSAGES, DEFAULT_SHORT_TTS_MESSAGES)

    val isAutoReconnectEnabled: Boolean
        get() = preferences.getBoolean(PREF_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT)

    val isTcpForced: Boolean
        get() = preferences.getBoolean(PREF_FORCE_TCP, DEFAULT_FORCE_TCP)

    val isOpusDisabled: Boolean
        get() = preferences.getBoolean(PREF_DISABLE_OPUS, DEFAULT_DISABLE_OPUS)

    val isTorEnabled: Boolean
        get() = preferences.getBoolean(PREF_USE_TOR, DEFAULT_USE_TOR)

    fun disableTor() {
        preferences.edit().putBoolean(PREF_USE_TOR, false).apply()
    }

    val isMuted: Boolean
        get() = preferences.getBoolean(PREF_MUTED, DEFAULT_MUTED)

    val isDeafened: Boolean
        get() = preferences.getBoolean(PREF_DEAFENED, DEFAULT_DEAFENED)

    val isFirstRun: Boolean
        get() = preferences.getBoolean(PREF_FIRST_RUN, DEFAULT_FIRST_RUN)

    fun shouldLoadExternalImages(): Boolean = preferences.getBoolean(PREF_LOAD_IMAGES, DEFAULT_LOAD_IMAGES)

    fun setMutedAndDeafened(muted: Boolean, deafened: Boolean) {
        preferences.edit()
            .putBoolean(PREF_MUTED, muted || deafened)
            .putBoolean(PREF_DEAFENED, deafened)
            .apply()
    }

    fun setFirstRun(run: Boolean) {
        preferences.edit().putBoolean(PREF_FIRST_RUN, run).apply()
    }

    val framesPerPacket: Int
        get() = preferences.getString(PREF_FRAMES_PER_PACKET, DEFAULT_FRAMES_PER_PACKET)!!.toInt()

    val isHalfDuplex: Boolean
        get() = preferences.getBoolean(PREF_HALF_DUPLEX, DEFAULT_HALF_DUPLEX)

    val isHandsetMode: Boolean
        get() = preferences.getBoolean(PREF_HANDSET_MODE, DEFAULT_HANDSET_MODE)

    val isPttSoundEnabled: Boolean
        get() = preferences.getBoolean(PREF_PTT_SOUND, DEFAULT_PTT_SOUND)

    val isPreprocessorEnabled: Boolean
        get() = preferences.getBoolean(PREF_PREPROCESSOR_ENABLED, DEFAULT_PREPROCESSOR_ENABLED)

    val echoCancellationMethod: String
        get() {
            val method = preferences.getString(PREF_ECHO_CANCELLATION_METHOD, DEFAULT_ECHO_CANCELLATION_METHOD)
            return if (method == "system") DEFAULT_ECHO_CANCELLATION_METHOD else method!!
        }

    fun shouldStayAwake(): Boolean = preferences.getBoolean(PREF_STAY_AWAKE, DEFAULT_STAY_AWAKE)

    fun setDefaultCertificateId(defaultCertificateId: Long) {
        preferences.edit().putLong(PREF_CERT_ID, defaultCertificateId).apply()
    }

    fun disableCertificate() {
        preferences.edit().putLong(PREF_CERT_ID, -1).apply()
    }

    val isUsingCertificate: Boolean
        get() = defaultCertificate >= 0

    fun shouldShowUserCount(): Boolean = preferences.getBoolean(PREF_SHOW_USER_COUNT, DEFAULT_SHOW_USER_COUNT)

    fun shouldStartUpInPinnedMode(): Boolean =
        preferences.getBoolean(PREF_START_UP_IN_PINNED_MODE, DEFAULT_START_UP_IN_PINNED_MODE)

    val newsShownVersions: Set<String>
        get() = preferences.getStringSet(PREF_NEWS_SHOWN_VERSIONS, emptySet()) ?: emptySet()

    fun addNewsShownVersions(versions: List<String>) {
        val shownVersions = HashSet(preferences.getStringSet(PREF_NEWS_SHOWN_VERSIONS, emptySet()) ?: emptySet())
        val validVersions = versions.filter { it.isNotEmpty() }
        if (shownVersions.addAll(validVersions)) {
            preferences.edit().putStringSet(PREF_NEWS_SHOWN_VERSIONS, shownVersions).apply()
        }
    }

    fun resetNewsShownVersion() {
        preferences.edit().putStringSet(PREF_NEWS_SHOWN_VERSIONS, emptySet()).apply()
    }

    companion object {
        private val TAG = Settings::class.java.name

        const val PREF_INPUT_METHOD = "audioInputMethod"
        const val ARRAY_INPUT_METHOD_VOICE = "voiceActivity"
        const val ARRAY_INPUT_METHOD_PTT = "ptt"
        const val ARRAY_INPUT_METHOD_CONTINUOUS = "continuous"
        @JvmField
        val ARRAY_INPUT_METHODS: Set<String> = setOf(
            ARRAY_INPUT_METHOD_VOICE,
            ARRAY_INPUT_METHOD_PTT,
            ARRAY_INPUT_METHOD_CONTINUOUS,
        )

        const val PREF_THRESHOLD = "vadThreshold"
        const val DEFAULT_THRESHOLD = 50

        const val PREF_PUSH_KEY = "talkKey"
        const val DEFAULT_PUSH_KEY = -1

        const val PREF_HOT_CORNER_KEY = "hotCorner"
        const val ARRAY_HOT_CORNER_NONE = "none"
        const val ARRAY_HOT_CORNER_TOP_LEFT = "topLeft"
        const val ARRAY_HOT_CORNER_BOTTOM_LEFT = "bottomLeft"
        const val ARRAY_HOT_CORNER_TOP_RIGHT = "topRight"
        const val ARRAY_HOT_CORNER_BOTTOM_RIGHT = "bottomRight"
        const val DEFAULT_HOT_CORNER = ARRAY_HOT_CORNER_NONE

        const val PREF_PUSH_BUTTON_HIDE_KEY = "hidePtt"
        const val DEFAULT_PUSH_BUTTON_HIDE = false

        const val PREF_PTT_TOGGLE = "togglePtt"
        const val DEFAULT_PTT_TOGGLE = false

        const val PREF_INPUT_RATE = "input_quality"
        const val DEFAULT_RATE = "48000"

        const val PREF_INPUT_QUALITY = "input_bitrate"
        const val DEFAULT_INPUT_QUALITY = 40000

        const val PREF_AMPLITUDE_BOOST = "inputVolume"
        const val DEFAULT_AMPLITUDE_BOOST = 100

        const val PREF_CHAT_NOTIFY = "chatNotify"
        const val DEFAULT_CHAT_NOTIFY = true

        const val PREF_USE_TTS = "useTts"
        const val DEFAULT_USE_TTS = true

        const val PREF_SHORT_TTS_MESSAGES = "shortTtsMessages"
        const val DEFAULT_SHORT_TTS_MESSAGES = false

        const val PREF_AUTO_RECONNECT = "autoReconnect"
        const val DEFAULT_AUTO_RECONNECT = true

        const val PREF_THEME = "theme"
        const val PREF_LANGUAGE = "language"

        const val PREF_PTT_BUTTON_HEIGHT = "pttButtonHeight"
        const val DEFAULT_PTT_BUTTON_HEIGHT = 150

        const val PREF_CERT_ID = "certificateId"

        const val PREF_DEFAULT_USERNAME = "defaultUsername"
        const val DEFAULT_DEFAULT_USERNAME = "Mumla_User"

        const val PREF_FORCE_TCP = "forceTcp"
        const val DEFAULT_FORCE_TCP = false

        const val PREF_USE_TOR = "useTor"
        const val DEFAULT_USE_TOR = false

        const val PREF_DISABLE_OPUS = "disableOpus"
        const val DEFAULT_DISABLE_OPUS = false

        const val PREF_MUTED = "muted"
        const val DEFAULT_MUTED = false

        const val PREF_DEAFENED = "deafened"
        const val DEFAULT_DEAFENED = false

        const val PREF_FIRST_RUN = "firstRun"
        const val DEFAULT_FIRST_RUN = true

        const val PREF_LOAD_IMAGES = "load_images"
        const val DEFAULT_LOAD_IMAGES = true

        const val PREF_FRAMES_PER_PACKET = "audio_per_packet"
        const val DEFAULT_FRAMES_PER_PACKET = "2"

        const val PREF_HALF_DUPLEX = "half_duplex"
        const val DEFAULT_HALF_DUPLEX = false

        const val PREF_HANDSET_MODE = "handset_mode"
        const val DEFAULT_HANDSET_MODE = false

        const val PREF_PTT_SOUND = "ptt_sound"
        const val DEFAULT_PTT_SOUND = false

        const val PREF_PREPROCESSOR_ENABLED = "preprocessor_enabled"
        const val DEFAULT_PREPROCESSOR_ENABLED = true

        const val PREF_ECHO_CANCELLATION_METHOD = "echo_cancellation_method"
        const val DEFAULT_ECHO_CANCELLATION_METHOD = "webrtc"

        const val PREF_STAY_AWAKE = "stay_awake"
        const val DEFAULT_STAY_AWAKE = false

        const val PREF_SHOW_USER_COUNT = "show_user_count"
        const val DEFAULT_SHOW_USER_COUNT = false

        const val PREF_START_UP_IN_PINNED_MODE = "startUpInPinnedMode"
        const val DEFAULT_START_UP_IN_PINNED_MODE = false

        const val PREF_NEWS_SHOWN_VERSIONS = "newsShownVersions"

        @JvmStatic
        fun getInstance(context: Context): Settings = Settings(context)
    }
}
