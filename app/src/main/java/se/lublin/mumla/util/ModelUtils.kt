package se.lublin.mumla.util

import se.lublin.humla.model.IChannel
import java.util.LinkedList

object ModelUtils {
    @JvmStatic
    fun getChannelList(channel: IChannel): List<IChannel> {
        return LinkedList<IChannel>().apply {
            addChannel(channel, this)
        }
    }

    private fun addChannel(channel: IChannel, channels: MutableList<IChannel>) {
        channels.add(channel)
        for (subchannel in channel.subchannels) {
            if (subchannel != null) {
                addChannel(subchannel, channels)
            }
        }
    }
}
