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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.util.UUID
import org.spongycastle.jce.provider.BouncyCastleProvider
import se.lublin.mumla.R
import se.lublin.mumla.db.MumlaSQLiteDatabase

class CertificateImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
        fileIntent.type = "*/*"
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(fileIntent, REQUEST_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_FILE) {
            return
        }

        if (resultCode == RESULT_CANCELED || data == null) {
            finish()
            return
        }

        val uri = data.data
        val inputStream = try {
            contentResolver.openInputStream(uri!!)
        } catch (exception: FileNotFoundException) {
            exception.printStackTrace()
            finish()
            return
        }

        val displayName = displayNameFor(uri)
        storeKeystore(CharArray(0), displayName, inputStream)
    }

    private fun displayNameFor(uri: Uri): String {
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else UUID.randomUUID().toString() + ".p12"
        } ?: (UUID.randomUUID().toString() + ".p12")
    }

    private fun storeKeystore(password: CharArray, fileName: String, input: InputStream?) {
        val keyStore = try {
            KeyStore.getInstance("PKCS12", BouncyCastleProvider()).apply {
                load(input, password)
            }
        } catch (exception: CertificateException) {
            val passwordField = EditText(this)
            passwordField.setHint(R.string.password)
            passwordField.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.decrypt_certificate)
                .setView(passwordField)
                .setOnCancelListener { finish() }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    storeKeystore(passwordField.text.toString().toCharArray(), fileName, input)
                }
                .show()
            return
        } catch (exception: KeyStoreException) {
            failInvalidCertificate(exception)
            return
        } catch (exception: IOException) {
            failInvalidCertificate(exception)
            return
        } catch (exception: NoSuchAlgorithmException) {
            failInvalidCertificate(exception)
            return
        }

        val output = ByteArrayOutputStream()
        try {
            keyStore.store(output, CharArray(0))
        } catch (exception: Exception) {
            exception.printStackTrace()
            Toast.makeText(this, R.string.certificate_load_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val database = MumlaSQLiteDatabase(this)
        database.addCertificate(fileName, output.toByteArray())
        database.close()

        Toast.makeText(this, getString(R.string.certificate_import_success, fileName), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun failInvalidCertificate(exception: Exception) {
        exception.printStackTrace()
        Toast.makeText(this, R.string.invalid_certificate, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val REQUEST_FILE = 0
    }
}
