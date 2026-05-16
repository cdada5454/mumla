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

package se.lublin.mumla.channel.comment

import se.lublin.humla.IHumlaService
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaObserver

class UserCommentFragment : AbstractCommentFragment() {
    override fun requestComment(service: IHumlaService) {
        if (!service.isConnected) {
            return
        }
        service.registerObserver(object : HumlaObserver() {
            override fun onUserStateUpdated(user: IUser?) {
                val comment = user?.comment
                if (user?.session == session && comment != null) {
                    loadComment(comment)
                    service.unregisterObserver(this)
                }
            }
        })
        service.HumlaSession().requestComment(session)
    }

    override fun editComment(service: IHumlaService, comment: String) {
        if (!service.isConnected) {
            return
        }
        service.HumlaSession().setUserComment(session, comment)
    }

    val session: Int
        get() = requireArguments().getInt("session")
}
