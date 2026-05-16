package se.lublin.humla

import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Message
import se.lublin.humla.model.ServerSettings
import se.lublin.humla.model.WhisperTarget
import se.lublin.humla.net.HumlaUDPMessageType
import se.lublin.humla.util.VoiceTargetMode

interface IHumlaSession {
    fun getTCPLatency(): Long
    fun getUDPLatency(): Long
    val maxBandwidth: Int
    val currentBandwidth: Int
    val serverVersion: Int
    val serverRelease: String
    val serverOSName: String
    val serverOSVersion: String
    val sessionId: Int
    val sessionUser: IUser
    val sessionChannel: IChannel
    fun getUser(session: Int): IUser?
    fun getChannel(id: Int): IChannel
    val rootChannel: IChannel
    val permissions: Int
    val transmitMode: Int
    val codec: HumlaUDPMessageType
    fun usingBluetoothSco(): Boolean
    fun enableBluetoothSco()
    fun disableBluetoothSco()
    val isTalking: Boolean
    fun setTalkingState(talking: Boolean)
    fun joinChannel(channel: Int)
    fun moveUserToChannel(session: Int, channel: Int)
    fun createChannel(parent: Int, name: String, description: String, position: Int, temporary: Boolean)
    fun sendAccessTokens(tokens: List<String>)
    fun requestBanList()
    fun requestUserList()
    fun requestPermissions(channel: Int)
    fun requestComment(session: Int)
    fun requestAvatar(session: Int)
    fun requestChannelDescription(channel: Int)
    fun registerUser(session: Int)
    fun kickBanUser(session: Int, reason: String, ban: Boolean)
    fun sendUserTextMessage(session: Int, message: String): Message
    fun sendChannelTextMessage(channel: Int, message: String, tree: Boolean): Message
    fun setUserComment(session: Int, comment: String)
    fun setUserTexture(session: Int, texture: ByteArray)
    fun setPrioritySpeaker(session: Int, priority: Boolean)
    fun removeChannel(channel: Int)
    fun setMuteDeafState(session: Int, mute: Boolean, deaf: Boolean)
    fun setSelfMuteDeafState(mute: Boolean, deaf: Boolean)
    fun linkChannels(channelA: IChannel, channelB: IChannel)
    fun unlinkChannels(channelA: IChannel, channelB: IChannel)
    fun unlinkAllChannels(channel: IChannel)
    fun registerWhisperTarget(target: WhisperTarget): Byte
    fun unregisterWhisperTarget(targetId: Byte)
    var voiceTargetId: Byte
    val voiceTargetMode: VoiceTargetMode
    val whisperTarget: WhisperTarget
    val serverSettings: ServerSettings
}
