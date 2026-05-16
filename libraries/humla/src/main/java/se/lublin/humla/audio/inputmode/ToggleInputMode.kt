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

package se.lublin.humla.audio.inputmode

import android.util.Log
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ToggleInputMode : IInputMode {
    private val toggleLock: Lock = ReentrantLock()
    private val toggleCondition: Condition = toggleLock.newCondition()

    var isTalkingOn: Boolean = false
        set(talking) {
            toggleLock.lock()
            try {
                field = talking
                toggleCondition.signalAll()
            } finally {
                toggleLock.unlock()
            }
        }

    fun toggleTalkingOn() {
        isTalkingOn = !isTalkingOn
    }

    override fun shouldTransmit(pcm: ShortArray, length: Int): Boolean = isTalkingOn

    override fun waitForInput() {
        toggleLock.lock()
        try {
            if (!isTalkingOn) {
                Log.v(TAG, "PTT: Suspending audio input.")
                val startTime = System.currentTimeMillis()
                try {
                    toggleCondition.await()
                } catch (exception: InterruptedException) {
                    Log.w(TAG, "Blocking for PTT interrupted, likely due to input thread shutdown.")
                }
                Log.v(TAG, "PTT: Suspended audio input for ${System.currentTimeMillis() - startTime}ms.")
            }
        } finally {
            toggleLock.unlock()
        }
    }

    companion object {
        private val TAG = ToggleInputMode::class.java.name
    }
}
