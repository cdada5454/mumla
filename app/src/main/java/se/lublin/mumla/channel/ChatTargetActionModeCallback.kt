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

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import se.lublin.mumla.R

open class ChatTargetActionModeCallback(
    private val provider: ChatTargetProvider,
    val chatTarget: ChatTargetProvider.ChatTarget,
) : ActionMode.Callback {
    override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        actionMode.title = chatTarget.name
        actionMode.setSubtitle(R.string.current_chat_target)
        provider.setChatTarget(chatTarget)
        return true
    }

    override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean = false

    override fun onDestroyActionMode(actionMode: ActionMode) {
        provider.setChatTarget(null)
    }
}
