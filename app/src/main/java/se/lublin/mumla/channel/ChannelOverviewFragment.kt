package se.lublin.mumla.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.IHumlaObserver
import se.lublin.mumla.R
import se.lublin.mumla.util.HumlaServiceFragment

class ChannelOverviewFragment : HumlaServiceFragment() {
    private var channelId = -1
    private var overviewState by mutableStateOf(ChannelOverviewState())

    private val observer: IHumlaObserver = object : HumlaObserver() {
        override fun onDisconnected(e: HumlaException?) {
            renderChannelInfo()
        }

        override fun onChannelStateUpdated(channel: IChannel?) {
            if (channel != null && channel.id == channelId) {
                renderChannelInfo()
            }
        }

        override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
            renderChannelInfo()
        }

        override fun onUserConnected(user: IUser?) {
            renderChannelInfo()
        }

        override fun onUserRemoved(user: IUser?, reason: String?) {
            renderChannelInfo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelId = arguments?.getInt(ARG_CHANNEL_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ChannelOverviewContent(overviewState)
            }
        }
        renderChannelInfo()
        return view
    }

    override fun getServiceObserver(): IHumlaObserver {
        return observer
    }

    override fun onServiceBound(service: IHumlaService?) {
        renderChannelInfo()
    }

    private fun renderChannelInfo() {
        val currentService = service
        if (currentService == null || !currentService.isConnected) {
            overviewState = ChannelOverviewState(description = getString(R.string.unknown))
            return
        }

        val channel = currentService.HumlaSession().getChannel(channelId)
        if (channel == null) {
            overviewState = ChannelOverviewState(description = getString(R.string.unknown))
            return
        }

        val users = channel.users.size
        val subchannels = channel.subchannels.size

        val description = channel.description
        overviewState = ChannelOverviewState(
            userCount = resources.getQuantityString(R.plurals.search_channel_users, users, users),
            subchannelCount = getString(R.string.channel_subchannels_count, subchannels),
            description = if (description.isNullOrEmpty()) getString(R.string.unknown) else description
        )
    }

    companion object {
        private const val ARG_CHANNEL_ID = "channel_id"

        @JvmStatic
        fun newInstance(channelId: Int): ChannelOverviewFragment {
            val fragment = ChannelOverviewFragment()
            fragment.arguments = Bundle().apply { putInt(ARG_CHANNEL_ID, channelId) }
            return fragment
        }
    }
}

private data class ChannelOverviewState(
    val userCount: String = "",
    val subchannelCount: String = "",
    val description: String = ""
)

@Composable
private fun ChannelOverviewContent(state: ChannelOverviewState) {
    val primaryText = colorResource(android.R.color.primary_text_light)
    val secondaryText = colorResource(android.R.color.secondary_text_light)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colorResource(R.color.chat_composer_surface))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicText(
                text = state.userCount,
                style = TextStyle(color = primaryText, fontSize = 15.sp)
            )
            BasicText(
                text = state.subchannelCount,
                style = TextStyle(color = primaryText, fontSize = 15.sp),
                modifier = Modifier.padding(top = 6.dp)
            )
            BasicText(
                text = stringResource(R.string.description),
                style = TextStyle(
                    color = secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(top = 12.dp)
            )
            BasicText(
                text = state.description,
                style = TextStyle(color = primaryText, fontSize = 14.sp),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
