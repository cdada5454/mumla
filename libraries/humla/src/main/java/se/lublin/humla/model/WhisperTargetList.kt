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

class WhisperTargetList {
    private val activeTargets = arrayOfNulls<WhisperTarget>(TARGET_MAX - TARGET_MIN + 1)
    private var takenIds = 0

    init {
        clear()
    }

    fun append(target: WhisperTarget): Byte {
        var freeId: Byte = (-1).toByte()
        var id = TARGET_MIN
        while (id <= TARGET_MAX) {
            if (takenIds and (1 shl id.toInt()) == 0) {
                freeId = id
                break
            }
            id++
        }
        if (freeId.toInt() != -1) {
            activeTargets[freeId - TARGET_MIN] = target
            takenIds = takenIds or (1 shl freeId.toInt())
        }

        return freeId
    }

    operator fun get(id: Byte): WhisperTarget? {
        if (id < TARGET_MIN || id > TARGET_MAX) {
            throw IndexOutOfBoundsException()
        }

        if (takenIds and (1 shl id.toInt()) == 0) {
            return null
        }

        return activeTargets[id - TARGET_MIN]
    }

    fun free(slot: Byte) {
        if (slot < TARGET_MIN || slot > TARGET_MAX) {
            throw IndexOutOfBoundsException()
        }

        takenIds = takenIds and (1 shl slot.toInt()).inv()
    }

    fun spaceRemaining(): Int {
        var counter = 0
        var id = TARGET_MIN
        while (id <= TARGET_MAX) {
            if (takenIds and (1 shl id.toInt()) == 0) {
                counter++
            }
            id++
        }
        return counter
    }

    fun clear() {
        takenIds = 0
    }

    companion object {
        const val TARGET_MIN: Byte = 1
        const val TARGET_MAX: Byte = 30
    }
}
