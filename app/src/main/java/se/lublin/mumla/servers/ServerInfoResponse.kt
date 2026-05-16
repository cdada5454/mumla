package se.lublin.mumla.servers

import se.lublin.humla.model.Server
import java.nio.ByteBuffer

class ServerInfoResponse {
    val identifier: Long
    val version: Int
    val currentUsers: Int
    val maximumUsers: Int
    val allowedBandwidth: Int
    val latency: Int
    val server: Server?
    val isDummy: Boolean

    constructor(server: Server, response: ByteArray, latency: Int) {
        val buffer = ByteBuffer.wrap(response)
        version = buffer.int
        identifier = buffer.long
        currentUsers = buffer.int
        maximumUsers = buffer.int
        allowedBandwidth = buffer.int
        this.latency = latency
        this.server = server
        isDummy = false
    }

    constructor() {
        identifier = 0L
        version = 0
        currentUsers = 0
        maximumUsers = 0
        allowedBandwidth = 0
        latency = 0
        server = null
        isDummy = true
    }

    val versionString: String
        get() {
            val versionBytes = ByteBuffer.allocate(4).putInt(version).array()
            return String.format("%d.%d.%d", versionBytes[1].toInt(), versionBytes[2].toInt(), versionBytes[3].toInt())
        }
}
