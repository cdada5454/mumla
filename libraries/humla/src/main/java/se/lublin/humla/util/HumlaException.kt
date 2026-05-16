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

package se.lublin.humla.util

import android.os.Parcel
import android.os.Parcelable
import se.lublin.humla.protobuf.Mumble

class HumlaException : Exception, Parcelable {
    val reason: HumlaDisconnectReason
    private var rejectValue: Mumble.Reject? = null
    private var userRemoveValue: Mumble.UserRemove? = null

    constructor(message: String, exception: Throwable, reason: HumlaDisconnectReason) : super(message, exception) {
        this.reason = reason
    }

    constructor(message: String, reason: HumlaDisconnectReason) : super(message) {
        this.reason = reason
    }

    constructor(exception: Throwable, reason: HumlaDisconnectReason) : super(exception) {
        this.reason = reason
    }

    constructor(reject: Mumble.Reject) : super("Rejected: " + reject.reason) {
        rejectValue = reject
        reason = HumlaDisconnectReason.REJECT
    }

    constructor(userRemove: Mumble.UserRemove) : super((if (userRemove.ban) "Banned: " else "Kicked: ") + userRemove.reason) {
        userRemoveValue = userRemove
        reason = HumlaDisconnectReason.USER_REMOVE
    }

    private constructor(parcel: Parcel) : super(parcel.readString(), parcel.readSerializable() as Throwable?) {
        reason = HumlaDisconnectReason.entries[parcel.readInt()]
        rejectValue = parcel.readSerializable() as Mumble.Reject?
        userRemoveValue = parcel.readSerializable() as Mumble.UserRemove?
    }

    val reject: Mumble.Reject
        get() = rejectValue!!

    val userRemove: Mumble.UserRemove
        get() = userRemoveValue!!

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(message)
        dest.writeSerializable(cause)
        dest.writeInt(reason.ordinal)
        dest.writeSerializable(rejectValue)
        dest.writeSerializable(userRemoveValue)
    }

    enum class HumlaDisconnectReason {
        REJECT,
        USER_REMOVE,
        CONNECTION_ERROR,
        OTHER_ERROR,
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<HumlaException> = object : Parcelable.Creator<HumlaException> {
            override fun createFromParcel(source: Parcel): HumlaException = HumlaException(source)

            override fun newArray(size: Int): Array<HumlaException?> = arrayOfNulls(size)
        }
    }
}
