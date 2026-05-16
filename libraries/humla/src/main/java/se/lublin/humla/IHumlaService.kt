/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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

package se.lublin.humla

import se.lublin.humla.model.Server
import se.lublin.humla.util.HumlaDisconnectedException
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.IHumlaObserver

interface IHumlaService {
    fun registerObserver(observer: IHumlaObserver)
    fun unregisterObserver(observer: IHumlaObserver)
    val isConnected: Boolean
    fun disconnect()
    val connectionState: HumlaService.ConnectionState
    val connectionError: HumlaException?
    val isReconnecting: Boolean
    fun cancelReconnect()
    val targetServer: Server

    @Throws(HumlaDisconnectedException::class)
    fun HumlaSession(): IHumlaSession
}
