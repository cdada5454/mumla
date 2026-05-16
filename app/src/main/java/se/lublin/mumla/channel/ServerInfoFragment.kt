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

package se.lublin.mumla.channel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lublin.humla.IHumlaService
import se.lublin.humla.net.HumlaUDPMessageType
import se.lublin.mumla.R
import se.lublin.mumla.util.HumlaServiceFragment
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * A fragment that displays known information from the remote server.
 */
class ServerInfoFragment : HumlaServiceFragment() {
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var pollFuture: ScheduledFuture<*>? = null
    private var infoState by mutableStateOf(ServerInfoState())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ServerInfoContent(infoState)
            }
        }
    }

    /**
     * Updates the info from the service.
     */
    @Throws(RemoteException::class)
    fun updateData() {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            return
        }

        val session = currentService.HumlaSession()
        val codecName = when (session.codec) {
            HumlaUDPMessageType.UDPVoiceOpus -> "Opus"
            HumlaUDPMessageType.UDPVoiceCELTBeta -> "CELT 0.11.0"
            HumlaUDPMessageType.UDPVoiceCELTAlpha -> "CELT 0.7.0"
            HumlaUDPMessageType.UDPVoiceSpeex -> "Speex"
            null -> "<null>"
            else -> "???"
        }

        val targetServer = currentService.targetServer
        infoState = ServerInfoState(
            protocol = getString(R.string.server_info_protocol, session.serverRelease),
            osVersion = getString(R.string.server_info_version, session.serverOSName, session.serverOSVersion),
            tcpLatency = getString(R.string.server_info_latency, session.getTCPLatency().toFloat() * 10.0.pow(-3).toFloat()),
            udpLatency = getString(R.string.server_info_latency, session.getUDPLatency().toFloat() * 10.0.pow(-3).toFloat()),
            host = getString(R.string.server_info_host, targetServer.srvHost, targetServer.srvPort),
            maxBandwidth = getString(R.string.server_info_max_bandwidth, session.maxBandwidth.toFloat() / 1000f),
            currentBandwidth = getString(R.string.server_info_current_bandwidth, session.currentBandwidth.toFloat() / 1000f),
            codec = getString(R.string.server_info_codec, codecName)
        )
    }

    override fun onServiceBound(service: IHumlaService?) {
        pollFuture = executorService.scheduleAtFixedRate(
            {
                handler.post {
                    try {
                        if (isVisible) {
                            updateData()
                        }
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
            },
            0,
            POLL_RATE.toLong(),
            TimeUnit.MILLISECONDS
        )
    }

    override fun onServiceUnbound() {
        pollFuture?.cancel(true)
        pollFuture = null
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdownNow()
    }

    companion object {
        private const val POLL_RATE = 1000
    }
}

private data class ServerInfoState(
    val protocol: String = "Protocol 0.0.0",
    val osVersion: String = "Some operating system",
    val tcpLatency: String = "0.0 ms average latency (0.0 deviation)",
    val udpLatency: String = "0.0 ms average latency (0.0 deviation)",
    val host: String = "Remote host www.example.com (port 0)",
    val maxBandwidth: String = "Maximum 0.0kbit/s",
    val currentBandwidth: String = "Current 0.0kbit/s",
    val codec: String = "Codec: Some codec"
)

@Composable
private fun ServerInfoContent(state: ServerInfoState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        ServerInfoHeading(stringResource(R.string.version))
        ServerInfoLine(state.protocol)
        ServerInfoLine(state.osVersion)

        ServerInfoHeading(stringResource(R.string.control_channel), topPadding = 16)
        ServerInfoLine(stringResource(R.string.tcp_encryption_mode))
        ServerInfoLine(state.tcpLatency)
        ServerInfoHostLine(state.host)

        ServerInfoHeading(stringResource(R.string.voice_channel), topPadding = 16)
        ServerInfoLine(stringResource(R.string.udp_encryption_mode))
        ServerInfoLine(state.udpLatency)

        ServerInfoHeading(stringResource(R.string.audio_bandwidth), topPadding = 16)
        ServerInfoLine(state.maxBandwidth)
        ServerInfoLine(state.currentBandwidth)
        ServerInfoLine(state.codec)
    }
}

@Composable
private fun ServerInfoHeading(text: String, topPadding: Int = 0) {
    BasicText(
        text = text,
        style = TextStyle(
            color = colorResource(android.R.color.primary_text_light),
            fontSize = 20.sp,
            fontFamily = FontFamily.SansSerif
        ),
        modifier = Modifier.padding(top = topPadding.dp, bottom = 8.dp)
    )
}

@Composable
private fun ServerInfoLine(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            color = colorResource(android.R.color.primary_text_light),
            fontSize = 14.sp
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ServerInfoHostLine(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            color = colorResource(android.R.color.secondary_text_light),
            fontSize = 12.sp
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
