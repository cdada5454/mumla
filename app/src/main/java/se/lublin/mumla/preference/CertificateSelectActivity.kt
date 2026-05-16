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

package se.lublin.mumla.preference

import android.content.DialogInterface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.db.DatabaseCertificate
import se.lublin.mumla.db.MumlaSQLiteDatabase

class CertificateSelectActivity : AppCompatActivity(), DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    private lateinit var certificates: List<ICertificateItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = Settings.getInstance(this)
        val certificateItems = mutableListOf<ICertificateItem>()
        certificateItems.add(NoCertificateItem(getString(R.string.no_certificate), settings))
        val database = MumlaSQLiteDatabase(this)
        for (certificate in database.getCertificates()) {
            certificateItems.add(CertificateItem(certificate, settings))
        }
        database.close()
        certificates = certificateItems

        showCertificateSelectionDialog()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        certificates[which].onActivate()
        finish()
    }

    private fun showCertificateSelectionDialog() {
        val defaultCertificatePosition = certificates.indexOfFirst { it.isDefault }

        val adapter = object : ArrayAdapter<ICertificateItem>(
            this,
            android.R.layout.select_dialog_singlechoice,
            certificates,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                return view
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pref_certificate_title)
            .setSingleChoiceItems(adapter, defaultCertificatePosition, this)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .setOnDismissListener(this)
    }

    override fun onDismiss(dialog: DialogInterface) {
        finish()
    }

    private interface ICertificateItem {
        fun onActivate()
        val isDefault: Boolean
    }

    private class CertificateItem(
        private val certificate: DatabaseCertificate,
        private val settings: Settings,
    ) : ICertificateItem {
        override fun onActivate() {
            settings.setDefaultCertificateId(certificate.id)
        }

        override val isDefault: Boolean
            get() = settings.defaultCertificate == certificate.id

        override fun toString(): String = certificate.name
    }

    private class NoCertificateItem(
        private val text: String,
        private val settings: Settings,
    ) : ICertificateItem {
        override fun onActivate() {
            settings.disableCertificate()
        }

        override val isDefault: Boolean
            get() = !settings.isUsingCertificate

        override fun toString(): String = text
    }
}
