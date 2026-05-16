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

package se.lublin.mumla.util

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException

object MumlaTrustStore {
    private const val STORE_FILE = "mumla-store.bks"
    private const val STORE_PASS = ""
    private const val STORE_FORMAT = "BKS"

    @JvmStatic
    @Throws(CertificateException::class, NoSuchAlgorithmException::class, IOException::class, KeyStoreException::class)
    fun getTrustStore(context: Context): KeyStore {
        val store = KeyStore.getInstance(STORE_FORMAT)
        try {
            context.openFileInput(STORE_FILE).use { inputStream ->
                store.load(inputStream, STORE_PASS.toCharArray())
            }
        } catch (exception: FileNotFoundException) {
            store.load(null, null)
        }
        return store
    }

    @JvmStatic
    @Throws(IOException::class, CertificateException::class, NoSuchAlgorithmException::class, KeyStoreException::class)
    fun saveTrustStore(context: Context, store: KeyStore) {
        context.openFileOutput(STORE_FILE, Context.MODE_PRIVATE).use { outputStream ->
            store.store(outputStream, STORE_PASS.toCharArray())
        }
    }

    @JvmStatic
    fun clearTrustStore(context: Context) {
        context.deleteFile(STORE_FILE)
    }

    @JvmStatic
    fun getTrustStorePath(context: Context): String? {
        val trustPath = File(context.filesDir, STORE_FILE)
        return if (trustPath.exists()) trustPath.absolutePath else null
    }

    @JvmStatic
    fun getTrustStoreFormat(): String = STORE_FORMAT

    @JvmStatic
    fun getTrustStorePassword(): String = STORE_PASS
}
