package se.lublin.mumla.servers

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.humla.model.Server
import se.lublin.mumla.R
import se.lublin.mumla.Settings

class ServerEditFragment : DialogFragment() {
    private lateinit var nameEdit: EditText
    private lateinit var hostEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var usernameEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var listener: ServerEditListener

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        try {
            listener = activity as ServerEditListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement ServerEditListener!")
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
            if (validate()) {
                listener.onServerEdited(action, createServer())
                dismiss()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val settings = Settings.getInstance(requireActivity())
        val actionName = when (action) {
            Action.ADD_ACTION -> getString(R.string.add)
            Action.EDIT_ACTION -> getString(android.R.string.ok)
        }

        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_server_edit, null, false)
        nameEdit = view.findViewById(R.id.server_edit_name)
        hostEdit = view.findViewById(R.id.server_edit_host)
        portEdit = view.findViewById(R.id.server_edit_port)
        usernameEdit = view.findViewById<EditText>(R.id.server_edit_username).apply {
            hint = settings.defaultUsername
        }
        passwordEdit = view.findViewById(R.id.server_edit_password)

        server?.let { oldServer ->
            nameEdit.setText(oldServer.name)
            hostEdit.setText(oldServer.host)
            if (oldServer.port != 0) {
                portEdit.setText(oldServer.port.toString())
            }
            usernameEdit.setText(oldServer.username)
            passwordEdit.setText(oldServer.password)
        }

        return MaterialAlertDialogBuilder(requireActivity())
            .setPositiveButton(actionName, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setView(view)
            .create()
    }

    fun createServer(): Server {
        val name = nameEdit.text.toString().trim()
        val host = hostEdit.text.toString().trim()
        val port = try {
            portEdit.text.toString().toInt()
        } catch (_: NumberFormatException) {
            0
        }
        val usernameText = usernameEdit.text.toString().trim()
        val username = if (usernameText == "") usernameEdit.hint.toString() else usernameText
        val password = passwordEdit.text.toString()
        val id = server?.id ?: -1

        return Server(id, name, host, port, username, password)
    }

    fun validate(): Boolean {
        if (hostEdit.text.isEmpty()) {
            hostEdit.error = getString(R.string.invalid_host)
            return false
        } else if (portEdit.text.isNotEmpty()) {
            try {
                val port = portEdit.text.toString().toInt()
                if (port < 1 || port > 65535) {
                    portEdit.error = getString(R.string.invalid_port_range)
                    return false
                }
            } catch (_: NumberFormatException) {
                portEdit.error = getString(R.string.invalid_port_range)
                return false
            }
        }
        return true
    }

    private val server: Server?
        get() = requireArguments().getParcelable(ARGUMENT_SERVER)

    private val action: Action
        get() = Action.values()[requireArguments().getInt(ARGUMENT_ACTION)]

    interface ServerEditListener {
        fun onServerEdited(action: Action, server: Server)
    }

    enum class Action {
        EDIT_ACTION,
        ADD_ACTION
    }

    companion object {
        private const val ARGUMENT_SERVER = "server"
        private const val ARGUMENT_ACTION = "action"

        @JvmStatic
        fun createServerEditDialog(
            server: Server?,
            action: Action
        ): DialogFragment {
            val args = Bundle().apply {
                putParcelable(ARGUMENT_SERVER, server)
                putInt(ARGUMENT_ACTION, action.ordinal)
            }
            return ServerEditFragment().apply {
                arguments = args
            }
        }
    }
}
