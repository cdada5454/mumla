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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import java.util.Collections
import org.jsoup.Jsoup
import se.lublin.humla.Constants
import se.lublin.humla.HumlaService
import se.lublin.humla.exception.AudioException
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Message
import se.lublin.humla.model.TalkState
import se.lublin.humla.model.WhisperTargetUsers
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.DrawerAdapter
import se.lublin.mumla.app.MumlaActivity
import se.lublin.mumla.service.ipc.TalkBroadcastReceiver
import se.lublin.mumla.util.HtmlUtils

class MumlaService : HumlaService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MumlaConnectionNotification.OnActionListener,
    MumlaReconnectNotification.OnActionListener,
    IMumlaService {

    private lateinit var settings: Settings
    private var notification: MumlaConnectionNotification? = null
    private lateinit var messageNotification: MumlaMessageNotification
    private var reconnectNotification: MumlaReconnectNotification? = null
    private lateinit var channelOverlay: MumlaOverlay
    private var proximityLock: PowerManager.WakeLock? = null
    private var pttSoundEnabled = false
    private var shortTtsMessagesEnabled = false
    private var errorShown = false
    private val messageLog = ArrayList<IChatMessage>()
    private val privateCallHistory = ArrayList<PrivateCallHistoryItem>()
    private var suppressNotifications = false
    private var tts: TextToSpeech? = null
    private var pendingIncomingCallSession: Int? = null
    private var pendingOutgoingCallSession: Int? = null
    private var privateCallPeerSession: Int? = null
    private var privateCallWhisperTargetId: Byte? = null
    private val ttsInitListener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.ERROR) {
            logWarning(getString(R.string.tts_failed))
        }
    }
    private lateinit var hotCorner: MumlaHotCorner
    private val callActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(EXTRA_CALL_SESSION, -1)
            when (intent.action) {
                BROADCAST_CALL_ACCEPT -> if (session >= 0) acceptPrivateCall(session)
                BROADCAST_CALL_REJECT -> if (session >= 0) rejectPrivateCall(session)
                BROADCAST_CALL_HANGUP -> hangupPrivateCall()
            }
        }
    }
    private val hotCornerListener = object : MumlaHotCorner.MumlaHotCornerListener {
        override fun onHotCornerDown() {
            onTalkKeyDown()
        }

        override fun onHotCornerUp() {
            onTalkKeyUp()
        }
    }
    private lateinit var talkReceiver: BroadcastReceiver

    private val observer = object : HumlaObserver() {
        override fun onConnecting() {
            reconnectNotification?.hide()
            reconnectNotification = null

            val tor = if (settings.isTorEnabled) " (Tor)" else ""
            notification = MumlaConnectionNotification.create(
                this@MumlaService,
                getString(R.string.mumlaConnecting) + tor,
                this@MumlaService,
            )
            notification?.show()
            errorShown = false
        }

        override fun onConnected() {
            notification?.let {
                val tor = if (settings.isTorEnabled) " (Tor)" else ""
                it.setCustomContentText(getString(R.string.connected) + tor)
                it.setActionsShown(true)
                it.show()
            }
        }

        override fun onDisconnected(exception: HumlaException?) {
            notification?.hide()
            notification = null
            if (exception != null && !suppressNotifications) {
                reconnectNotification = MumlaReconnectNotification.show(
                    this@MumlaService,
                    exception.message + if (settings.isTorEnabled) " (Tor)" else "",
                    isReconnecting,
                    this@MumlaService,
                )
            }
        }

        override fun onUserConnected(user: IUser?) {
            if (user == null) return
            if (user.textureHash != null && user.texture == null) {
                requestAvatar(user.session)
            }
        }

        override fun onUserStateUpdated(user: IUser?) {
            if (user == null) return
            val selfSession = try {
                sessionId
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "exception in onUserStateUpdated: $exception")
                return
            }

            if (user.session == selfSession) {
                settings.setMutedAndDeafened(user.isSelfMuted, user.isSelfDeafened)
                notification?.let {
                    val contentText = when {
                        user.isSelfMuted && user.isSelfDeafened -> getString(R.string.status_notify_muted_and_deafened)
                        user.isSelfMuted -> getString(R.string.status_notify_muted)
                        else -> getString(R.string.connected)
                    }
                    it.setCustomContentText(contentText)
                    it.show()
                }
            }

            if (user.textureHash != null && user.texture == null) {
                requestAvatar(user.session)
            }
        }

        override fun onMessageLogged(message: IMessage) {
            if (handleCallSignal(message)) {
                return
            }

            val parsedMessage = Jsoup.parseBodyFragment(message.message)
            val strippedMessage = parsedMessage.text()

            val ttsMessage = if (shortTtsMessagesEnabled) {
                for (anchor in parsedMessage.getElementsByTag("A")) {
                    val href = anchor.attr("href")
                    if (href != null && href == anchor.text()) {
                        val urlHostname = HtmlUtils.getHostnameFromLink(href)
                        if (urlHostname != null) {
                            anchor.text(getString(R.string.chat_message_tts_short_link, urlHostname))
                        }
                    }
                }
                parsedMessage.text()
            } else {
                strippedMessage
            }

            val formattedTtsMessage = getString(R.string.notification_message, message.actorName, ttsMessage)
            val currentTts = tts
            if (settings.isTextToSpeechEnabled &&
                currentTts != null &&
                formattedTtsMessage.length <= TTS_THRESHOLD &&
                sessionUser != null &&
                !sessionUser.isSelfDeafened
            ) {
                currentTts.speak(formattedTtsMessage, TextToSpeech.QUEUE_ADD, null)
            }

            if (settings.isChatNotifyEnabled) {
                messageNotification.show(message)
            }

            messageLog.add(IChatMessage.TextMessage(message))
        }

        override fun onLogInfo(message: String) {
            messageLog.add(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.INFO, message))
        }

        override fun onLogWarning(message: String) {
            messageLog.add(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.WARNING, message))
        }

        override fun onLogError(message: String) {
            messageLog.add(IChatMessage.InfoMessage(IChatMessage.InfoMessage.Type.ERROR, message))
        }

        override fun onPermissionDenied(reason: String) {
            if (notification != null && !suppressNotifications) {
                notification?.show()
            }
        }

        override fun onUserTalkStateUpdated(user: IUser?) {
            if (user == null) return
            val selfSession = try {
                sessionId
            } catch (exception: IllegalStateException) {
                Log.d(TAG, "exception in onUserTalkStateUpdated: $exception")
                -1
            }

            if (isConnectionEstablished &&
                user.session == selfSession &&
                transmitMode == Constants.TRANSMIT_PUSH_TO_TALK &&
                user.talkState == TalkState.TALKING &&
                pttSoundEnabled
            ) {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerObserver(observer)

        settings = Settings.getInstance(this)
        pttSoundEnabled = settings.isPttSoundEnabled
        shortTtsMessagesEnabled = settings.isShortTextToSpeechMessagesEnabled
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        setTheme(R.style.Theme_Mumla)

        messageNotification = MumlaMessageNotification(this)
        channelOverlay = MumlaOverlay(this)
        hotCorner = MumlaHotCorner(this, settings.hotCornerGravity, hotCornerListener)
        registerCallActionReceiver()

        if (settings.isTextToSpeechEnabled) {
            tts = TextToSpeech(this, ttsInitListener)
        }

        talkReceiver = TalkBroadcastReceiver(this)
    }

    override fun onBind(intent: Intent?): IBinder = MumlaBinder(this)

    override fun onDestroy() {
        notification?.hide()
        notification = null
        reconnectNotification?.hide()
        reconnectNotification = null

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        try {
            unregisterReceiver(talkReceiver)
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }
        try {
            unregisterReceiver(callActionReceiver)
        } catch (exception: IllegalArgumentException) {
            exception.printStackTrace()
        }

        unregisterObserver(observer)
        tts?.shutdown()
        messageLog.clear()
        messageNotification.dismiss()
        super.onDestroy()
    }

    override fun onConnectionSynchronized() {
        try {
            super.onConnectionSynchronized()
        } catch (exception: RuntimeException) {
            Log.d(TAG, "exception in onConnectionSynchronized: $exception")
            return
        }

        if (settings.isMuted || settings.isDeafened) {
            setSelfMuteDeafState(settings.isMuted, settings.isDeafened)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(talkReceiver, IntentFilter(TalkBroadcastReceiver.BROADCAST_TALK), RECEIVER_EXPORTED)
        } else {
            registerReceiver(talkReceiver, IntentFilter(TalkBroadcastReceiver.BROADCAST_TALK))
        }

        if (settings.isHotCornerEnabled) {
            hotCorner.setShown(true)
        }
        if (settings.isHandsetMode) {
            setProximitySensorOn(true)
        }
    }

    override fun onConnectionDisconnected(exception: HumlaException?) {
        super.onConnectionDisconnected(exception)
        try {
            unregisterReceiver(talkReceiver)
        } catch (_: IllegalArgumentException) {
        }

        channelOverlay.hide()
        hotCorner.setShown(false)
        setProximitySensorOn(false)
        clearPrivateCallState(sendHangup = false)
        clearMessageLog()
        privateCallHistory.clear()
        messageNotification.dismiss()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val changedExtras = Bundle()
        var requiresReconnect = false
        when (key) {
            Settings.PREF_INPUT_METHOD -> {
                val inputMethod = settings.humlaInputMethod
                changedExtras.putInt(EXTRAS_TRANSMIT_MODE, inputMethod)
                channelOverlay.setPushToTalkShown(inputMethod == Constants.TRANSMIT_PUSH_TO_TALK)
            }
            Settings.PREF_HANDSET_MODE -> {
                setProximitySensorOn(isConnectionEstablished && settings.isHandsetMode)
                changedExtras.putInt(
                    EXTRAS_AUDIO_STREAM,
                    if (settings.isHandsetMode) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC,
                )
            }
            Settings.PREF_THRESHOLD -> {
                changedExtras.putFloat(EXTRAS_DETECTION_THRESHOLD, settings.detectionThreshold)
            }
            Settings.PREF_HOT_CORNER_KEY -> {
                hotCorner.gravity = settings.hotCornerGravity
                hotCorner.setShown(isConnectionEstablished && settings.isHotCornerEnabled)
            }
            Settings.PREF_USE_TTS -> {
                if (tts == null && settings.isTextToSpeechEnabled) {
                    tts = TextToSpeech(this, ttsInitListener)
                } else if (tts != null && !settings.isTextToSpeechEnabled) {
                    tts?.shutdown()
                    tts = null
                }
            }
            Settings.PREF_SHORT_TTS_MESSAGES -> {
                shortTtsMessagesEnabled = settings.isShortTextToSpeechMessagesEnabled
            }
            Settings.PREF_AMPLITUDE_BOOST -> {
                changedExtras.putFloat(EXTRAS_AMPLITUDE_BOOST, settings.amplitudeBoostMultiplier)
            }
            Settings.PREF_HALF_DUPLEX -> {
                changedExtras.putBoolean(EXTRAS_HALF_DUPLEX, settings.isHalfDuplex)
            }
            Settings.PREF_PREPROCESSOR_ENABLED -> {
                changedExtras.putBoolean(EXTRAS_ENABLE_PREPROCESSOR, settings.isPreprocessorEnabled)
            }
            Settings.PREF_ECHO_CANCELLATION_METHOD -> {
                changedExtras.putString(EXTRAS_ECHO_CANCELLATION_METHOD, settings.echoCancellationMethod)
            }
            Settings.PREF_PTT_SOUND -> {
                pttSoundEnabled = settings.isPttSoundEnabled
            }
            Settings.PREF_INPUT_QUALITY -> {
                changedExtras.putInt(EXTRAS_INPUT_QUALITY, settings.inputQuality)
            }
            Settings.PREF_INPUT_RATE -> {
                changedExtras.putInt(EXTRAS_INPUT_RATE, settings.inputSampleRate)
            }
            Settings.PREF_FRAMES_PER_PACKET -> {
                changedExtras.putInt(EXTRAS_FRAMES_PER_PACKET, settings.framesPerPacket)
            }
            Settings.PREF_CERT_ID,
            Settings.PREF_FORCE_TCP,
            Settings.PREF_USE_TOR,
            Settings.PREF_DISABLE_OPUS,
            -> {
                requiresReconnect = true
            }
        }
        if (changedExtras.size() > 0) {
            try {
                requiresReconnect = requiresReconnect or configureExtras(changedExtras)
            } catch (exception: AudioException) {
                exception.printStackTrace()
            }
        }

        if (requiresReconnect && isConnectionEstablished) {
            Toast.makeText(this, R.string.change_requires_reconnect, Toast.LENGTH_LONG).show()
        }
    }

    private fun setProximitySensorOn(on: Boolean) {
        if (on) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            proximityLock = powerManager.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Mumla:Proximity")
            proximityLock?.acquire()
        } else {
            proximityLock?.release()
            proximityLock = null
        }
    }

    override fun onMuteToggled() {
        val user = sessionUser
        if (isConnectionEstablished && user != null) {
            val muted = !user.isSelfMuted
            val deafened = user.isSelfDeafened && muted
            setSelfMuteDeafState(muted, deafened)
        }
    }

    override fun onDeafenToggled() {
        val user = sessionUser
        if (isConnectionEstablished && user != null) {
            setSelfMuteDeafState(!user.isSelfDeafened, !user.isSelfDeafened)
        }
    }

    override fun onOverlayToggled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val close = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            applicationContext.sendBroadcast(close)
        }

        if (!channelOverlay.isShown) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(applicationContext)) {
                    val showSetting = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                    showSetting.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(showSetting)
                    Toast.makeText(this, R.string.grant_perm_draw_over_apps, Toast.LENGTH_LONG).show()
                    return
                }
            }
            channelOverlay.show()
        } else {
            channelOverlay.hide()
        }
    }

    override fun onReconnectNotificationDismissed() {
        errorShown = true
    }

    override fun reconnect() {
        connect()
    }

    override fun cancelReconnect() {
        reconnectNotification?.hide()
        reconnectNotification = null
        super.cancelReconnect()
    }

    override fun setOverlayShown(showOverlay: Boolean) {
        if (!channelOverlay.isShown) {
            channelOverlay.show()
        } else {
            channelOverlay.hide()
        }
    }

    override fun isOverlayShown(): Boolean = channelOverlay.isShown

    override fun clearChatNotifications() {
        messageNotification.dismiss()
    }

    override fun markErrorShown() {
        errorShown = true
        if (reconnectNotification != null && !isReconnecting) {
            reconnectNotification?.hide()
            reconnectNotification = null
        }
    }

    override fun isErrorShown(): Boolean = errorShown

    override fun onTalkKeyDown() {
        if (isConnectionEstablished && Settings.ARRAY_INPUT_METHOD_PTT == settings.inputMethod) {
            if (!settings.isPushToTalkToggle && !isTalking) {
                setTalkingState(true)
            }
        }
    }

    override fun onTalkKeyUp() {
        if (isConnectionEstablished && Settings.ARRAY_INPUT_METHOD_PTT == settings.inputMethod) {
            if (settings.isPushToTalkToggle) {
                setTalkingState(!isTalking)
            } else if (isTalking) {
                setTalkingState(false)
            }
        }
    }

    override fun getMessageLog(): List<IChatMessage> = Collections.unmodifiableList(messageLog)

    override fun clearMessageLog() {
        messageLog.clear()
    }

    override fun getRecentChatHistory(): List<ChatHistoryItem> {
        val selfSession = try {
            sessionId
        } catch (exception: IllegalStateException) {
            Log.d(TAG, "getRecentChatHistory() while session is not ready", exception)
            return emptyList()
        }
        val recentBySession = LinkedHashMap<Int, ChatHistoryItem>()
        for (chatMessage in messageLog.asReversed()) {
            val textMessage = chatMessage as? IChatMessage.TextMessage ?: continue
            val message = textMessage.message
            if (isCallSignal(message.message)) continue
            val peer = privateChatPeer(message, selfSession) ?: continue
            if (recentBySession.containsKey(peer.session)) continue
            recentBySession[peer.session] = ChatHistoryItem(
                session = peer.session,
                userName = peer.name,
                latestTime = message.receivedTime,
                preview = Jsoup.parseBodyFragment(message.message).text(),
            )
        }
        return recentBySession.values.toList()
    }

    override fun getPrivateCallHistory(): List<PrivateCallHistoryItem> = privateCallHistory.toList()

    override fun setSuppressNotifications(suppressNotifications: Boolean) {
        this.suppressNotifications = suppressNotifications
    }

    override fun sendUserTextMessage(session: Int, message: String): Message {
        val msg = super.sendUserTextMessage(session, message)
        if (!isCallSignal(message)) {
            messageLog.add(IChatMessage.TextMessage(msg))
        }
        return msg
    }

    override fun sendChannelTextMessage(channel: Int, message: String, tree: Boolean): Message {
        val msg = super.sendChannelTextMessage(channel, message, tree)
        messageLog.add(IChatMessage.TextMessage(msg))
        return msg
    }

    override fun startPrivateCall(session: Int) {
        if (!isConnectionEstablished || session == sessionId) return
        val target = getUser(session) ?: return
        recordPrivateCall(session, target.name)
        pendingOutgoingCallSession = session
        notifyPrivateCallState(CALL_STATE_OUTGOING, session)
        sendCallSignal(session, CALL_TYPE_CALL)
        showCallStatusNotification(
            title = getString(R.string.private_call_outgoing_title),
            text = getString(R.string.private_call_outgoing_text, target.name),
            peerSession = session,
            ringing = false,
            incoming = false,
        )
    }

    override fun acceptPrivateCall(session: Int) {
        if (!isConnectionEstablished) return
        pendingIncomingCallSession = null
        sendCallSignal(session, CALL_TYPE_ACCEPT)
        beginPrivateCall(session)
    }

    override fun rejectPrivateCall(session: Int) {
        pendingIncomingCallSession = null
        if (isConnectionEstablished) {
            sendCallSignal(session, CALL_TYPE_REJECT)
        }
        notifyPrivateCallState(CALL_STATE_ENDED, session)
        dismissPrivateCallNotification()
    }

    override fun hangupPrivateCall() {
        clearPrivateCallState(sendHangup = true)
    }

    override fun getPendingIncomingPrivateCallSession(): Int? = pendingIncomingCallSession

    override fun getPendingOutgoingPrivateCallSession(): Int? = pendingOutgoingCallSession

    override fun getActivePrivateCallSession(): Int? = privateCallPeerSession

    override fun getPrivateCallMicLevel(): Float = audioInputLevel

    private fun beginPrivateCall(session: Int) {
        val target = getUser(session) ?: return
        clearPrivateCallState(sendHangup = false)

        val targetId = registerWhisperTarget(WhisperTargetUsers(target))
        if (targetId < 0) {
            Toast.makeText(this, R.string.private_call_failed, Toast.LENGTH_SHORT).show()
            return
        }

        privateCallPeerSession = session
        privateCallWhisperTargetId = targetId
        voiceTargetId = targetId
        setAllowedVoiceSession(session)
        enablePhoneCallTransmitMode()
        notifyPrivateCallState(CALL_STATE_ACTIVE, session)
        showCallStatusNotification(
            title = getString(R.string.private_call_active_title),
            text = getString(R.string.private_call_active_text, target.name),
            peerSession = session,
            ringing = false,
            incoming = false,
        )
        Toast.makeText(this, getString(R.string.private_call_active_text, target.name), Toast.LENGTH_SHORT).show()
    }

    private fun clearPrivateCallState(sendHangup: Boolean) {
        val peerSession = privateCallPeerSession ?: pendingOutgoingCallSession ?: pendingIncomingCallSession
        if (sendHangup && peerSession != null && isConnectionEstablished) {
            sendCallSignal(peerSession, CALL_TYPE_HANGUP)
        }

        privateCallWhisperTargetId?.let { unregisterWhisperTarget(it) }
        privateCallWhisperTargetId = null
        privateCallPeerSession = null
        pendingOutgoingCallSession = null
        pendingIncomingCallSession = null
        if (isConnectionEstablished) {
            voiceTargetId = 0
            setAllowedVoiceSession(null)
            restorePhoneCallTransmitMode()
        }
        if (peerSession != null) {
            notifyPrivateCallState(CALL_STATE_ENDED, peerSession)
        }
        dismissPrivateCallNotification()
    }

    private fun handleCallSignal(message: IMessage): Boolean {
        val signal = parseCallSignal(message.message) ?: return false
        val remoteSession = if (signal.fromSession >= 0) signal.fromSession else message.actor
        if (remoteSession < 0 || remoteSession == sessionId) return true

        when (signal.type) {
            CALL_TYPE_CALL -> {
                pendingIncomingCallSession = remoteSession
                val caller = getUser(remoteSession)
                recordPrivateCall(remoteSession, caller?.name ?: message.actorName ?: remoteSession.toString())
                notifyPrivateCallState(CALL_STATE_INCOMING, remoteSession)
                showCallStatusNotification(
                    title = getString(R.string.private_call_incoming_title),
                    text = getString(R.string.private_call_incoming_text, caller?.name ?: message.actorName ?: remoteSession.toString()),
                    peerSession = remoteSession,
                    ringing = true,
                    incoming = true,
                )
            }
            CALL_TYPE_ACCEPT -> {
                if (pendingOutgoingCallSession == remoteSession || privateCallPeerSession == null) {
                    pendingOutgoingCallSession = null
                    beginPrivateCall(remoteSession)
                }
            }
            CALL_TYPE_REJECT -> {
                if (pendingOutgoingCallSession == remoteSession) {
                    clearPrivateCallState(sendHangup = false)
                    Toast.makeText(this, R.string.private_call_rejected, Toast.LENGTH_SHORT).show()
                }
            }
            CALL_TYPE_HANGUP -> {
                if (privateCallPeerSession == remoteSession || pendingIncomingCallSession == remoteSession || pendingOutgoingCallSession == remoteSession) {
                    clearPrivateCallState(sendHangup = false)
                    Toast.makeText(this, R.string.private_call_ended, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    private fun sendCallSignal(session: Int, type: String) {
        super.sendUserTextMessage(session, "<!--mumla-call:type=$type;from=$sessionId-->")
    }

    private fun privateChatPeer(message: IMessage, selfSession: Int): ChatPeer? {
        if (message.targetUsers.isEmpty() ||
            message.targetChannels.isNotEmpty() ||
            message.targetTrees.isNotEmpty()
        ) {
            return null
        }
        return if (message.actor == selfSession) {
            val target = message.targetUsers.firstOrNull { it.session != selfSession } ?: return null
            ChatPeer(target.session, target.name)
        } else {
            val name = message.actorName ?: runCatching { getUser(message.actor)?.name }.getOrNull()
            ChatPeer(message.actor, name ?: message.actor.toString())
        }
    }

    private fun recordPrivateCall(session: Int, userName: String) {
        privateCallHistory.add(0, PrivateCallHistoryItem(session, userName, System.currentTimeMillis()))
        if (privateCallHistory.size > MAX_PRIVATE_CALL_HISTORY) {
            privateCallHistory.removeAt(privateCallHistory.lastIndex)
        }
    }

    private fun enablePhoneCallTransmitMode() {
        setForceTransmit(true)
        setTalkingState(true)
    }

    private fun restorePhoneCallTransmitMode() {
        setForceTransmit(false)
        setTalkingState(false)
    }

    private fun notifyPrivateCallState(state: String, session: Int) {
        sendBroadcast(
            Intent(MumlaActivity.ACTION_PRIVATE_CALL_STATE)
                .setPackage(packageName)
                .putExtra(MumlaActivity.EXTRA_PRIVATE_CALL_STATE, state)
                .putExtra(MumlaActivity.EXTRA_PRIVATE_CALL_SESSION, session)
        )
    }

    private fun parseCallSignal(message: String): CallSignal? {
        if (!isCallSignal(message)) return null
        val body = message.removePrefix("<!--mumla-call:").removeSuffix("-->")
        val values = body.split(';')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
        val type = values["type"] ?: return null
        val from = values["from"]?.toIntOrNull() ?: -1
        return CallSignal(type, from)
    }

    private fun isCallSignal(message: String): Boolean = message.startsWith("<!--mumla-call:") && message.endsWith("-->")

    private fun registerCallActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(BROADCAST_CALL_ACCEPT)
            addAction(BROADCAST_CALL_REJECT)
            addAction(BROADCAST_CALL_HANGUP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(callActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callActionReceiver, filter)
        }
    }

    private fun showCallStatusNotification(
        title: String,
        text: String,
        peerSession: Int,
        ringing: Boolean,
        incoming: Boolean,
    ) {
        var channelId = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = PRIVATE_CALL_CHANNEL_ID
            val importance = if (ringing) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, getString(R.string.private_call_channel), importance)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(this, MumlaActivity::class.java).apply {
            putExtra(MumlaActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER)
            if (incoming) {
                putExtra(MumlaActivity.EXTRA_PRIVATE_CALL_SESSION, peerSession)
                putExtra(MumlaActivity.EXTRA_PRIVATE_CALL_STATE, CALL_STATE_INCOMING)
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            20,
            contentIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (ringing) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setOngoing(privateCallPeerSession == peerSession)
            .setAutoCancel(privateCallPeerSession != peerSession)

        if (ringing) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                .setVibrate(PRIVATE_CALL_VIBRATION_PATTERN)
                .setFullScreenIntent(contentPendingIntent, true)
        }

        if (incoming) {
            builder.addAction(R.drawable.call_24px, getString(R.string.private_call_accept), callActionIntent(BROADCAST_CALL_ACCEPT, peerSession, 21))
            builder.addAction(R.drawable.cancel_24px, getString(R.string.private_call_reject), callActionIntent(BROADCAST_CALL_REJECT, peerSession, 22))
        } else {
            builder.addAction(R.drawable.cancel_24px, getString(R.string.private_call_hangup), callActionIntent(BROADCAST_CALL_HANGUP, peerSession, 23))
        }

        try {
            NotificationManagerCompat.from(this).notify(PRIVATE_CALL_NOTIFICATION_ID, builder.build())
        } catch (exception: SecurityException) {
            Log.w(TAG, "Unable to show private call notification", exception)
        }
    }

    private fun callActionIntent(action: String, session: Int, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName).putExtra(EXTRA_CALL_SESSION, session)
        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dismissPrivateCallNotification() {
        NotificationManagerCompat.from(this).cancel(PRIVATE_CALL_NOTIFICATION_ID)
    }

    class MumlaBinder internal constructor(val service: IMumlaService) : Binder()

    companion object {
        private val TAG = MumlaService::class.java.name

        const val PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32
        const val TTS_THRESHOLD = 250
        const val RECONNECT_DELAY = 10000
        private const val PRIVATE_CALL_NOTIFICATION_ID = 3
        private const val PRIVATE_CALL_CHANNEL_ID = "private_call_channel"
        private const val BROADCAST_CALL_ACCEPT = "se.lublin.mumla.CALL_ACCEPT"
        private const val BROADCAST_CALL_REJECT = "se.lublin.mumla.CALL_REJECT"
        private const val BROADCAST_CALL_HANGUP = "se.lublin.mumla.CALL_HANGUP"
        private const val EXTRA_CALL_SESSION = "session"
        private const val CALL_TYPE_CALL = "call"
        private const val CALL_TYPE_ACCEPT = "accept"
        private const val CALL_TYPE_REJECT = "reject"
        private const val CALL_TYPE_HANGUP = "hangup"
        private const val MAX_PRIVATE_CALL_HISTORY = 100
        const val CALL_STATE_INCOMING = "incoming"
        const val CALL_STATE_OUTGOING = "outgoing"
        const val CALL_STATE_ACTIVE = "active"
        const val CALL_STATE_ENDED = "ended"
        private val PRIVATE_CALL_VIBRATION_PATTERN = longArrayOf(0, 400, 250, 400)
    }

    private data class CallSignal(val type: String, val fromSession: Int)
    private data class ChatPeer(val session: Int, val name: String)
}
