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

package se.lublin.mumla.channel

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.TalkState
import se.lublin.humla.model.User
import se.lublin.mumla.R
import se.lublin.mumla.drawable.CircleDrawable

class ChannelAdapter(private val context: Context, private var currentChannel: IChannel) : BaseAdapter() {
    override fun getCount(): Int = currentChannel.users.size

    override fun getItem(position: Int): Any = currentChannel.users[position]

    override fun getItemId(position: Int): Long = currentChannel.users[position]?.userId?.toLong() ?: -1

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.overlay_user_row, parent, false)
        val user = getItem(position) as User
        val titleView = view.findViewById<TextView>(R.id.user_row_name)
        titleView.text = user.name

        val state = view.findViewById<ImageView>(R.id.user_row_state)
        state.setImageDrawable(getUserStateDrawable(user))

        return view
    }

    private fun getUserStateDrawable(user: User): Drawable {
        val resources = context.resources
        when {
            user.isSelfDeafened -> return resources.getDrawable(R.drawable.outline_circle_deafened)
            user.isSelfMuted -> return resources.getDrawable(R.drawable.outline_circle_muted)
            user.isDeafened -> return resources.getDrawable(R.drawable.outline_circle_server_deafened)
            user.isMuted -> return resources.getDrawable(R.drawable.outline_circle_server_muted)
            user.isSuppressed -> return resources.getDrawable(R.drawable.outline_circle_suppressed)
            user.isSpeaking() -> return resources.getDrawable(R.drawable.outline_circle_talking_on)
        }

        val texture = user.texture ?: return resources.getDrawable(R.drawable.outline_circle_talking_quiet)
        val bitmap = BitmapFactory.decodeByteArray(texture, 0, texture.size)
        return if (bitmap != null) {
            CircleDrawable(resources, bitmap, Color.TRANSPARENT)
        } else {
            resources.getDrawable(R.drawable.outline_circle_talking_quiet)
        }
    }

    private fun User.isSpeaking(): Boolean {
        return !isSelfDeafened &&
            !isSelfMuted &&
            !isDeafened &&
            !isMuted &&
            !isSuppressed &&
            (talkState == TalkState.TALKING ||
                talkState == TalkState.SHOUTING ||
                talkState == TalkState.WHISPERING)
    }

    fun setChannel(channel: IChannel) {
        currentChannel = channel
        notifyDataSetChanged()
    }

    fun getChannel(): IChannel = currentChannel
}
