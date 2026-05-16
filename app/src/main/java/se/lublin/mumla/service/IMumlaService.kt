package se.lublin.mumla.service

import se.lublin.humla.IHumlaService

interface IMumlaService : IHumlaService {
    fun setOverlayShown(showOverlay: Boolean)

    fun isOverlayShown(): Boolean

    fun clearChatNotifications()

    fun markErrorShown()

    fun isErrorShown(): Boolean

    fun onTalkKeyDown()

    fun onTalkKeyUp()

    fun getMessageLog(): List<IChatMessage>

    fun clearMessageLog()

    fun getRecentChatHistory(): List<ChatHistoryItem>

    fun getPrivateCallHistory(): List<PrivateCallHistoryItem>

    fun setSuppressNotifications(suppressNotifications: Boolean)

    fun startPrivateCall(session: Int)

    fun acceptPrivateCall(session: Int)

    fun rejectPrivateCall(session: Int)

    fun hangupPrivateCall()

    fun getPendingIncomingPrivateCallSession(): Int?

    fun getPendingOutgoingPrivateCallSession(): Int?

    fun getActivePrivateCallSession(): Int?

    fun getPrivateCallMicLevel(): Float
}

data class ChatHistoryItem(
    val session: Int,
    val userName: String,
    val latestTime: Long,
    val preview: String,
)

data class PrivateCallHistoryItem(
    val session: Int,
    val userName: String,
    val time: Long,
)
