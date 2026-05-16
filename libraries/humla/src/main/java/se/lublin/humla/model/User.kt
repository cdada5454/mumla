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

import com.google.protobuf.ByteString

class User() : IUser, Comparable<User> {
    override var session: Int = 0
        private set
    override var userId: Int = -1
    override var name: String = ""
    override var comment: String? = null
    private var commentHashBytes: ByteString? = null
    private var textureBytes: ByteString? = null
    private var textureHashBytes: ByteString? = null
    override var hash: String? = null
    override var isMuted: Boolean = false
    override var isDeafened: Boolean = false
    override var isSuppressed: Boolean = false
    override var isSelfMuted: Boolean = false
    override var isSelfDeafened: Boolean = false
    override var isPrioritySpeaker: Boolean = false
    override var isRecording: Boolean = false
    override var isLocalMuted: Boolean = false
    override var isLocalIgnored: Boolean = false
    override var talkState: TalkState = TalkState.PASSIVE
    var averageAvailable: Float = 0f

    override var channel: Channel? = null
        set(value) {
            field?.removeUser(this)
            field = value
            field?.addUser(this)
        }

    override val commentHash: ByteArray?
        get() = commentHashBytes?.toByteArray()

    override val texture: ByteArray?
        get() = textureBytes?.toByteArray()

    override val textureHash: ByteArray?
        get() = textureHashBytes?.toByteArray()

    constructor(session: Int, name: String) : this() {
        this.session = session
        this.name = name
    }

    fun setCommentHash(commentHash: ByteString?) {
        commentHashBytes = commentHash
    }

    fun setTexture(texture: ByteString?) {
        textureBytes = texture
    }

    fun setTextureHash(textureHash: ByteString?) {
        textureHashBytes = textureHash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as User
        return session == other.session
    }

    override fun hashCode(): Int = userId

    override fun compareTo(other: User): Int = name.lowercase().compareTo(other.name.lowercase())
}
