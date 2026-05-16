package se.lublin.mumla.servers

import android.os.AsyncTask
import android.util.Log
import se.lublin.humla.model.Server
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

open class ServerInfoTask : AsyncTask<Server, Void, ServerInfoResponse>() {
    override fun doInBackground(vararg params: Server): ServerInfoResponse {
        val server = params[0]
        try {
            val buffer = ByteBuffer.allocate(12)
            buffer.putInt(0)
            buffer.putLong(server.id)
            val requestPacket = DatagramPacket(
                buffer.array(),
                12,
                InetAddress.getByName(server.srvHost),
                server.srvPort
            )

            DatagramSocket().use { socket ->
                socket.soTimeout = 1000
                socket.receiveBufferSize = 1024

                val startTime = System.nanoTime()
                socket.send(requestPacket)

                val responseBuffer = ByteArray(24)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                val latencyInMs = ((System.nanoTime() - startTime) / 1_000_000).toInt()
                val response = ServerInfoResponse(server, responseBuffer, latencyInMs)

                Log.d(
                    TAG,
                    "Server version: ${response.versionString} Users: ${response.currentUsers}/${response.maximumUsers}"
                )

                return response
            }
        } catch (_: Exception) {
        }

        return ServerInfoResponse()
    }

    companion object {
        private val TAG = ServerInfoTask::class.java.name
    }
}
