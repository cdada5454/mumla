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

package se.lublin.humla.protocol

import se.lublin.humla.protobuf.Mumble

interface HumlaTCPMessageListener {
    fun messageAuthenticate(msg: Mumble.Authenticate)
    fun messageBanList(msg: Mumble.BanList)
    fun messageReject(msg: Mumble.Reject)
    fun messageServerSync(msg: Mumble.ServerSync)
    fun messageServerConfig(msg: Mumble.ServerConfig)
    fun messagePermissionDenied(msg: Mumble.PermissionDenied)
    fun messageUDPTunnel(msg: Mumble.UDPTunnel)
    fun messageUserState(msg: Mumble.UserState)
    fun messageUserRemove(msg: Mumble.UserRemove)
    fun messageChannelState(msg: Mumble.ChannelState)
    fun messageChannelRemove(msg: Mumble.ChannelRemove)
    fun messageTextMessage(msg: Mumble.TextMessage)
    fun messageACL(msg: Mumble.ACL)
    fun messageQueryUsers(msg: Mumble.QueryUsers)
    fun messagePing(msg: Mumble.Ping)
    fun messageCryptSetup(msg: Mumble.CryptSetup)
    fun messageContextAction(msg: Mumble.ContextAction)
    fun messageContextActionModify(msg: Mumble.ContextActionModify)
    fun messageRemoveContextAction(msg: Mumble.ContextActionModify)
    fun messageVersion(msg: Mumble.Version)
    fun messageUserList(msg: Mumble.UserList)
    fun messagePermissionQuery(msg: Mumble.PermissionQuery)
    fun messageCodecVersion(msg: Mumble.CodecVersion)
    fun messageUserStats(msg: Mumble.UserStats)
    fun messageRequestBlob(msg: Mumble.RequestBlob)
    fun messageSuggestConfig(msg: Mumble.SuggestConfig)
    fun messageVoiceTarget(msg: Mumble.VoiceTarget)

    open class Stub : HumlaTCPMessageListener {
        override fun messageAuthenticate(msg: Mumble.Authenticate) = Unit
        override fun messageBanList(msg: Mumble.BanList) = Unit
        override fun messageReject(msg: Mumble.Reject) = Unit
        override fun messageServerSync(msg: Mumble.ServerSync) = Unit
        override fun messageServerConfig(msg: Mumble.ServerConfig) = Unit
        override fun messagePermissionDenied(msg: Mumble.PermissionDenied) = Unit
        override fun messageUDPTunnel(msg: Mumble.UDPTunnel) = Unit
        override fun messageUserState(msg: Mumble.UserState) = Unit
        override fun messageUserRemove(msg: Mumble.UserRemove) = Unit
        override fun messageChannelState(msg: Mumble.ChannelState) = Unit
        override fun messageChannelRemove(msg: Mumble.ChannelRemove) = Unit
        override fun messageTextMessage(msg: Mumble.TextMessage) = Unit
        override fun messageACL(msg: Mumble.ACL) = Unit
        override fun messageQueryUsers(msg: Mumble.QueryUsers) = Unit
        override fun messagePing(msg: Mumble.Ping) = Unit
        override fun messageCryptSetup(msg: Mumble.CryptSetup) = Unit
        override fun messageContextAction(msg: Mumble.ContextAction) = Unit
        override fun messageContextActionModify(msg: Mumble.ContextActionModify) = Unit
        override fun messageRemoveContextAction(msg: Mumble.ContextActionModify) = Unit
        override fun messageVersion(msg: Mumble.Version) = Unit
        override fun messageUserList(msg: Mumble.UserList) = Unit
        override fun messagePermissionQuery(msg: Mumble.PermissionQuery) = Unit
        override fun messageCodecVersion(msg: Mumble.CodecVersion) = Unit
        override fun messageUserStats(msg: Mumble.UserStats) = Unit
        override fun messageRequestBlob(msg: Mumble.RequestBlob) = Unit
        override fun messageSuggestConfig(msg: Mumble.SuggestConfig) = Unit
        override fun messageVoiceTarget(msg: Mumble.VoiceTarget) = Unit
    }
}
