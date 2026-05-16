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

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.IHumlaService
import se.lublin.humla.net.Permissions
import se.lublin.mumla.R
import se.lublin.mumla.util.HumlaServiceProvider

class ChannelEditFragment : DialogFragment() {
    private lateinit var serviceProvider: HumlaServiceProvider
    private lateinit var nameField: TextView
    private lateinit var descriptionField: TextView
    private lateinit var positionField: TextView
    private lateinit var temporaryBox: CheckBox

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        serviceProvider = activity as? HumlaServiceProvider
            ?: throw ClassCastException("$activity must implement HumlaServiceProvider")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.fragment_channel_edit, null, false)
        nameField = view.findViewById(R.id.channel_edit_name)
        descriptionField = view.findViewById(R.id.channel_edit_description)
        positionField = view.findViewById(R.id.channel_edit_position)
        temporaryBox = view.findViewById(R.id.channel_edit_temporary)

        val service = serviceProvider.getService()
        if (service != null && service.isConnected) {
            if (!isSuperUser(service)) {
                val parentPermissions = getParentPermissions(service)
                val canMakeChannel = (parentPermissions and Permissions.MakeChannel) > 0
                val canMakeTempChannel = (parentPermissions and Permissions.MakeTempChannel) > 0
                val onlyTemp = canMakeTempChannel && !canMakeChannel
                temporaryBox.isChecked = onlyTemp
                temporaryBox.isEnabled = !onlyTemp
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isAdding) R.string.channel_add else R.string.channel_edit)
            .setView(view)
            .setPositiveButton(if (isAdding) R.string.add else R.string.save) { _, _ ->
                val currentService = serviceProvider.getService()
                if (isAdding && currentService != null && currentService.isConnected) {
                    val temporary = if (isSuperUser(currentService)) {
                        temporaryBox.isChecked
                    } else {
                        val parentPermissions = getParentPermissions(currentService)
                        val canMakeChannel = (parentPermissions and Permissions.MakeChannel) > 0
                        val canMakeTempChannel = (parentPermissions and Permissions.MakeTempChannel) > 0
                        val checkedTemporary = if (canMakeTempChannel && !canMakeChannel) {
                            true
                        } else {
                            temporaryBox.isChecked
                        }
                        if ((!checkedTemporary && !canMakeChannel) || (checkedTemporary && !canMakeTempChannel)) {
                            return@setPositiveButton
                        }
                        checkedTemporary
                    }
                    currentService.HumlaSession().createChannel(
                        parent,
                        nameField.text.toString(),
                        descriptionField.text.toString(),
                        positionField.text.toString().toInt(),
                        temporary,
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun getParentPermissions(service: IHumlaService): Int {
        val session = service.HumlaSession()
        return if (parent == 0) session.permissions else session.getChannel(parent).permissions
    }

    private fun isSuperUser(service: IHumlaService): Boolean =
        service.HumlaSession().sessionUser.name == "SuperUser"

    val isAdding: Boolean
        get() = arguments?.getBoolean("adding") == true

    val parent: Int
        get() = requireArguments().getInt("parent")

    val channel: Int
        get() = requireArguments().getInt("channel")
}
