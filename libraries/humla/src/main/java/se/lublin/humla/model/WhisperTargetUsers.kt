/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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

import se.lublin.humla.protobuf.Mumble

class WhisperTargetUsers(
    private val sessions: List<Int>,
    override val name: String,
) : WhisperTarget {
    constructor(user: IUser) : this(listOf(user.session), user.name)
    constructor(session: Int, name: String) : this(listOf(session), name)

    override fun createTarget(): Mumble.VoiceTarget.Target {
        val builder = Mumble.VoiceTarget.Target.newBuilder()
        for (session in sessions) {
            builder.addSession(session)
        }
        return builder.build()
    }
}
