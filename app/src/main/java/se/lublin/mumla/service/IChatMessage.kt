/*
 * Copyright (C) 2015 Andrew Comminos
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

package se.lublin.mumla.service

import se.lublin.humla.model.IMessage

/**
 * A general chat message, either a text message from a user or an
 * informational notice.
 */
interface IChatMessage {
    /**
     * @return The body of the message.
     */
    fun getBody(): String

    /**
     * @return the unix timestamp when the message was received.
     */
    fun getReceivedTime(): Long

    /**
     * Calls the provided visitor object with the proper message implementation.
     * @param visitor A visitor object responding to the underlying chat message type.
     */
    fun accept(visitor: Visitor)

    /**
     * A text message from a user.
     */
    class TextMessage(
        val message: IMessage
    ) : IChatMessage {
        override fun getBody(): String = message.message

        override fun getReceivedTime(): Long = message.receivedTime

        override fun accept(visitor: Visitor) {
            visitor.visit(this)
        }
    }

    /**
     * An informational message about the server or client state.
     */
    class InfoMessage(
        val type: Type,
        private val body: String
    ) : IChatMessage {
        private val receivedTime = System.currentTimeMillis()

        override fun getBody(): String = body

        override fun getReceivedTime(): Long = receivedTime

        override fun accept(visitor: Visitor) {
            visitor.visit(this)
        }

        /**
         * The type of informational message.
         */
        enum class Type {
            INFO,
            WARNING,
            ERROR
        }
    }

    interface Visitor {
        fun visit(message: TextMessage)
        fun visit(message: InfoMessage)
    }
}
