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

package se.lublin.humla.util

import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser

class HumlaCallbacks : IHumlaObserver {
    private val callbacks: MutableSet<IHumlaObserver> =
        Collections.newSetFromMap(ConcurrentHashMap<IHumlaObserver, Boolean>())

    fun registerObserver(observer: IHumlaObserver) {
        callbacks.add(observer)
    }

    fun unregisterObserver(observer: IHumlaObserver) {
        callbacks.remove(observer)
    }

    override fun onConnected() {
        callbacks.forEach { it.onConnected() }
    }

    override fun onConnecting() {
        callbacks.forEach { it.onConnecting() }
    }

    override fun onDisconnected(e: HumlaException?) {
        callbacks.forEach { it.onDisconnected(e) }
    }

    override fun onTLSHandshakeFailed(chain: Array<X509Certificate>) {
        callbacks.forEach { it.onTLSHandshakeFailed(chain) }
    }

    override fun onChannelAdded(channel: IChannel?) {
        callbacks.forEach { it.onChannelAdded(channel) }
    }

    override fun onChannelStateUpdated(channel: IChannel?) {
        callbacks.forEach { it.onChannelStateUpdated(channel) }
    }

    override fun onChannelRemoved(channel: IChannel?) {
        callbacks.forEach { it.onChannelRemoved(channel) }
    }

    override fun onChannelPermissionsUpdated(channel: IChannel?) {
        callbacks.forEach { it.onChannelPermissionsUpdated(channel) }
    }

    override fun onUserConnected(user: IUser?) {
        callbacks.forEach { it.onUserConnected(user) }
    }

    override fun onUserStateUpdated(user: IUser?) {
        callbacks.forEach { it.onUserStateUpdated(user) }
    }

    override fun onUserTalkStateUpdated(user: IUser?) {
        callbacks.forEach { it.onUserTalkStateUpdated(user) }
    }

    override fun onUserJoinedChannel(user: IUser?, newChannel: IChannel?, oldChannel: IChannel?) {
        callbacks.forEach { it.onUserJoinedChannel(user, newChannel, oldChannel) }
    }

    override fun onUserRemoved(user: IUser?, reason: String?) {
        callbacks.forEach { it.onUserRemoved(user, reason) }
    }

    override fun onPermissionDenied(reason: String) {
        callbacks.forEach { it.onPermissionDenied(reason) }
    }

    override fun onMessageLogged(message: IMessage) {
        callbacks.forEach { it.onMessageLogged(message) }
    }

    override fun onVoiceTargetChanged(mode: VoiceTargetMode?) {
        callbacks.forEach { it.onVoiceTargetChanged(mode) }
    }

    override fun onLogInfo(message: String) {
        callbacks.forEach { it.onLogInfo(message) }
    }

    override fun onLogWarning(message: String) {
        callbacks.forEach { it.onLogWarning(message) }
    }

    override fun onLogError(message: String) {
        callbacks.forEach { it.onLogError(message) }
    }
}
