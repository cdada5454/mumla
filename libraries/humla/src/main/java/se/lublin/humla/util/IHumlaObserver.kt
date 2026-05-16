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

package se.lublin.humla.util

import java.security.cert.X509Certificate
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser

interface IHumlaObserver {
    fun onConnected()
    fun onConnecting()
    fun onDisconnected(e: HumlaException?)
    fun onTLSHandshakeFailed(chain: Array<X509Certificate>)
    fun onChannelAdded(channel: IChannel?)
    fun onChannelStateUpdated(channel: IChannel?)
    fun onChannelRemoved(channel: IChannel?)
    fun onChannelPermissionsUpdated(channel: IChannel?)
    fun onUserConnected(user: IUser?)
    fun onUserStateUpdated(user: IUser?)
    fun onUserTalkStateUpdated(user: IUser?)
    fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?)
    fun onUserRemoved(user: IUser?, reason: String?)
    fun onPermissionDenied(reason: String)
    fun onMessageLogged(message: IMessage)
    fun onVoiceTargetChanged(mode: VoiceTargetMode?)
    fun onLogInfo(message: String)
    fun onLogWarning(message: String)
    fun onLogError(message: String)
}
