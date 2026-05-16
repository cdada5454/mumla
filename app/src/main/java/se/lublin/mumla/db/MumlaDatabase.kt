package se.lublin.mumla.db

import se.lublin.humla.model.Server

interface MumlaDatabase {
    fun open()
    fun close()

    fun getServers(): List<Server>
    fun addServer(server: Server)
    fun updateServer(server: Server)
    fun removeServer(server: Server)

    fun isCommentSeen(hash: String, commentHash: ByteArray): Boolean
    fun markCommentSeen(hash: String, commentHash: ByteArray)

    fun getPinnedChannels(serverId: Long): List<Int>
    fun addPinnedChannel(serverId: Long, channelId: Int)
    fun removePinnedChannel(serverId: Long, channelId: Int)
    fun isChannelPinned(serverId: Long, channelId: Int): Boolean

    fun getAccessTokens(serverId: Long): List<String>
    fun addAccessToken(serverId: Long, token: String)
    fun removeAccessToken(serverId: Long, token: String)

    fun getLocalMutedUsers(serverId: Long): List<Int>
    fun addLocalMutedUser(serverId: Long, userId: Int)
    fun removeLocalMutedUser(serverId: Long, userId: Int)

    fun getLocalIgnoredUsers(serverId: Long): List<Int>
    fun addLocalIgnoredUser(serverId: Long, userId: Int)
    fun removeLocalIgnoredUser(serverId: Long, userId: Int)

    fun addCertificate(name: String, certificate: ByteArray): DatabaseCertificate
    fun getCertificates(): List<DatabaseCertificate>
    fun getCertificateData(id: Long): ByteArray?
    fun removeCertificate(id: Long)
}
