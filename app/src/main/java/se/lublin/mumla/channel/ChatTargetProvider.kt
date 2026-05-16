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

import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.mumla.BuildConfig

interface ChatTargetProvider {
    class ChatTarget {
        val channel: IChannel?
        val user: IUser?

        constructor(channel: IChannel) {
            this.channel = channel
            user = null
        }

        constructor(user: IUser) {
            channel = null
            this.user = user
        }

        val name: String
            get() {
                user?.let { return it.name }
                channel?.let { return it.name }
                if (BuildConfig.DEBUG) {
                    throw RuntimeException()
                }
                return "Unknown"
            }
    }

    fun interface OnChatTargetSelectedListener {
        fun onChatTargetSelected(target: ChatTarget?)
    }

    fun getChatTarget(): ChatTarget?

    fun setChatTarget(target: ChatTarget?)

    fun registerChatTargetListener(listener: OnChatTargetSelectedListener)

    fun unregisterChatTargetListener(listener: OnChatTargetSelectedListener)
}
