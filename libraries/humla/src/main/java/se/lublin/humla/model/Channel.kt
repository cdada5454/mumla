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

package se.lublin.humla.model

import java.util.Collections

class Channel(
    override var id: Int = 0,
    override var isTemporary: Boolean = false
) : IChannel, Comparable<Channel> {
    override var position: Int = 0
    var level: Int = 0
    override var parent: Channel? = null
    override var name: String = ""
    override var description: String? = null
    override var descriptionHash: ByteArray? = null
    private val mutableSubchannels = ArrayList<Channel>()
    private val mutableUsers = ArrayList<User>()
    private val mutableLinks = ArrayList<Channel>()
    override var permissions: Int = 0

    fun addUser(user: User) {
        for (index in mutableUsers.indices) {
            val existingUser = mutableUsers[index]
            if (user.compareTo(existingUser) <= 0) {
                mutableUsers.add(index, user)
                return
            }
        }
        mutableUsers.add(user)
    }

    fun removeUser(user: User) {
        mutableUsers.remove(user)
    }

    override val users: List<User>
        get() = Collections.unmodifiableList(mutableUsers)

    override val subchannels: List<Channel>
        get() = Collections.unmodifiableList(mutableSubchannels)

    fun addSubchannel(channel: Channel) {
        for (index in mutableSubchannels.indices) {
            val subchannel = mutableSubchannels[index]
            if (channel.compareTo(subchannel) <= 0) {
                mutableSubchannels.add(index, channel)
                return
            }
        }
        mutableSubchannels.add(channel)
    }

    fun removeSubchannel(channel: Channel) {
        mutableSubchannels.remove(channel)
    }

    override val links: List<Channel>
        get() = Collections.unmodifiableList(mutableLinks)

    fun addLink(channel: Channel) {
        for (index in mutableLinks.indices) {
            val linkedChannel = mutableLinks[index]
            if (channel.compareTo(linkedChannel) <= 0) {
                mutableLinks.add(index, channel)
                return
            }
        }
        mutableLinks.add(channel)
    }

    fun removeLink(channel: Channel) {
        mutableLinks.remove(channel)
    }

    fun clearLinks() {
        mutableLinks.clear()
    }

    override val subchannelUserCount: Int
        get() {
            var userCount = mutableUsers.size
            for (subchannel in mutableSubchannels) {
                userCount += subchannel.subchannelUserCount
            }
            return userCount
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as Channel
        return id == other.id
    }

    override fun hashCode(): Int = id

    override fun compareTo(other: Channel): Int {
        if (position != other.position) {
            return position.compareTo(other.position)
        }
        return name.compareTo(other.name)
    }
}
