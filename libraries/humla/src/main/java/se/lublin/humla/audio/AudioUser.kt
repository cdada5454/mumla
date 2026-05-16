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

package se.lublin.humla.audio

import kotlin.math.ceil
import kotlin.math.max

class AudioUser(val session: Int) {
    private var averageAvailable = 0f

    fun updateAveragePackets(numPackets: Int) {
        averageAvailable = max(numPackets.toFloat(), averageAvailable * AVERAGE_DECAY)
    }

    val averagePackets: Int
        get() = ceil(averageAvailable).toInt()

    companion object {
        private const val AVERAGE_DECAY = 0.99f
    }
}
