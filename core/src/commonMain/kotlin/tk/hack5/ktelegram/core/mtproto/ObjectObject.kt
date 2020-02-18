/*
 *     KTelegram (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("ObjectPropertyName", "ClassName", "LocalVariableName", "MemberVisibilityCanBePrivate")

package tk.hack5.ktelegram.core.mtproto

import tk.hack5.ktelegram.core.TlMappings
import tk.hack5.ktelegram.core.tl.TLConstructor
import tk.hack5.ktelegram.core.tl.TLObject


/**
 * This class is used as a kind of shim, to make the type system understand that an Object can just be some plain bytes
 * This type is always bare and serializes exactly to the TL binary repr of the object it is passed, without any header
 * No validation or verification takes place on the `data` param; it exists for optimisation
 */
data class ObjectObject(val innerObject: TLObject<*>) : ObjectType {
    override val bare = true

    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        return innerObject.toTlRepr()
    }

    override val native = this

    @ExperimentalUnsignedTypes
    override val _id
        get() = null

    companion object : TLConstructor<ObjectType> {
        @ExperimentalUnsignedTypes
        override val _id
            get() = null

        /**
         * Returns an ObjectObject (bare wrapper) if the object doesn't fit as a ObjectType
         * If the object fits as an ObjectType (i.e. it's a GzipWrapped) we return that
         */
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray): Pair<Int, ObjectType>? {
            val innerObject = (TlMappings.CONSTRUCTORS[data.first()]
                ?: error("Attempting to deserialize unrecognized datatype")).fromTlRepr(data)
                ?: error("Unable to deserialize data")
            return Pair(innerObject.first, innerObject.second as? ObjectType ?: ObjectObject(innerObject.second))
        }
    }
}

