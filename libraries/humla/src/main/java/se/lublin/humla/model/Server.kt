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

package se.lublin.humla.model

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.google.common.net.InetAddresses
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.minidns.hla.ResolverApi
import org.minidns.util.SrvUtil
import se.lublin.humla.Constants

open class Server(
    var id: Long,
    private var customName: String?,
    host: String,
    port: Int,
    var username: String?,
    var password: String?
) : Parcelable {
    private var resolvedHost: String? = null
    private var resolvedPort: Int = 0

    var host: String = host
        set(value) {
            field = value
            resolvedHost = null
        }

    var port: Int = port
        set(value) {
            field = value
            resolvedHost = null
        }

    val name: String
        get() = if (!customName.isNullOrEmpty()) customName!! else host

    fun setName(name: String?) {
        customName = name
    }

    val isSaved: Boolean
        get() = id != -1L

    val srvHost: String
        get() {
            srvResolve()
            return resolvedHost!!
        }

    val srvPort: Int
        get() {
            srvResolve()
            return resolvedPort
        }

    private constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(customName)
        parcel.writeString(host)
        parcel.writeInt(port)
        parcel.writeString(username)
        parcel.writeString(password)
    }

    override fun describeContents(): Int = 0

    @Synchronized
    private fun srvResolve() {
        if (resolvedHost != null) {
            return
        }
        if (port != 0) {
            resolvedHost = host
            resolvedPort = port
            return
        }
        if (InetAddresses.isInetAddress(host) || host.endsWith(".onion")) {
            resolvedHost = host
            resolvedPort = Constants.DEFAULT_PORT
            return
        }
        val srvHost = AtomicReference(host)
        val srvPort = AtomicInteger(Constants.DEFAULT_PORT)
        try {
            val thread = Thread {
                try {
                    val lookup = "_mumble._tcp.${srvHost.get()}"
                    val result = ResolverApi.INSTANCE.resolveSrv(lookup)
                    if (!result.wasSuccessful()) {
                        Log.d(TAG, "resolveSrv $lookup: ${result.responseCode}")
                        return@Thread
                    }
                    val answers = result.answersOrEmptySet
                    if (answers.isEmpty()) {
                        Log.d(TAG, "resolveSrv $lookup: empty answer")
                        return@Thread
                    }
                    val records = SrvUtil.sortSrvRecords(answers)
                    for (srv in records) {
                        Log.d(TAG, "resolved $lookup SRV: $srv")
                        srvHost.set(srv.target.toString())
                        srvPort.set(srv.port)
                        return@Thread
                    }
                } catch (exception: IOException) {
                    Log.d(TAG, "exception in srvResolve: $exception")
                } catch (exception: IllegalArgumentException) {
                    Log.d(TAG, "exception in srvResolve: $exception")
                }
            }
            thread.start()
            thread.join()
        } catch (exception: Exception) {
            Log.d(TAG, "resolveSRV() $exception")
        }
        resolvedHost = srvHost.get()
        resolvedPort = srvPort.get()
    }

    companion object {
        private val TAG = Server::class.java.name

        @JvmField
        val CREATOR: Parcelable.Creator<Server> = object : Parcelable.Creator<Server> {
            override fun createFromParcel(parcel: Parcel): Server = Server(parcel)
            override fun newArray(size: Int): Array<Server?> = arrayOfNulls(size)
        }
    }
}
