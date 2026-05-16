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
import java.util.Date

class Message : IMessage {
    override var actor: Int
        private set
    override var actorName: String?
        private set
    private var channels: List<Channel>
    private var trees: List<Channel>
    private var users: List<User>
    override var message: String
        private set
    override var receivedTime: Long
        private set

    constructor(message: String) {
        this.message = message
        actor = -1
        actorName = null
        receivedTime = Date().time
        channels = ArrayList()
        trees = ArrayList()
        users = ArrayList()
    }

    constructor(
        actor: Int,
        actorName: String?,
        channels: List<Channel>,
        trees: List<Channel>,
        users: List<User>,
        message: String,
    ) : this(message) {
        this.actor = actor
        this.actorName = actorName
        this.channels = channels
        this.trees = trees
        this.users = users
    }

    override val targetChannels: List<Channel>
        get() = Collections.unmodifiableList(channels)

    override val targetTrees: List<Channel>
        get() = Collections.unmodifiableList(trees)

    override val targetUsers: List<User>
        get() = Collections.unmodifiableList(users)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Message

        if (actor != other.actor) return false
        if (receivedTime != other.receivedTime) return false
        if (actorName != other.actorName) return false
        if (channels != other.channels) return false
        if (message != other.message) return false
        if (trees != other.trees) return false
        if (users != other.users) return false

        return true
    }

    override fun hashCode(): Int {
        var result = actor
        result = 31 * result + (actorName?.hashCode() ?: 0)
        result = 31 * result + channels.hashCode()
        result = 31 * result + trees.hashCode()
        result = 31 * result + users.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (receivedTime xor (receivedTime ushr 32)).toInt()
        return result
    }

    @Deprecated("")
    enum class Type {
        INFO,
        WARNING,
        TEXT_MESSAGE,
    }
}
