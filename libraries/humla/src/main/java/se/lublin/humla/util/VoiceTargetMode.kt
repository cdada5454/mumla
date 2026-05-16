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

package se.lublin.humla.util

enum class VoiceTargetMode {
    NORMAL,
    WHISPER,
    SERVER_LOOPBACK;

    companion object {
        @JvmStatic
        fun fromId(targetId: Byte): VoiceTargetMode {
            return when {
                targetId.toInt() == 0 -> NORMAL
                targetId > 0 && targetId < 31 -> WHISPER
                targetId.toInt() == 31 -> SERVER_LOOPBACK
                else -> throw IllegalArgumentException()
            }
        }
    }
}
