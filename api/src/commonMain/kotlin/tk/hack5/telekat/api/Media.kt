/*
 *     TeleKat (Telegram MTProto client library)
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

package tk.hack5.telekat.api

import tk.hack5.telekat.core.tl.*

fun MessageMediaType.toInputMedia(): InputMediaType {
    return when (this) {
        is MessageMediaEmptyObject -> InputMediaEmptyObject()
        is MessageMediaPhotoObject -> TODO()
        is MessageMediaGeoObject -> InputMediaGeoPointObject(geo.toInputGeoPoint())
        is MessageMediaContactObject -> InputMediaContactObject(phoneNumber, firstName, lastName, vcard)
        is MessageMediaUnsupportedObject -> InputMediaEmptyObject()
        is MessageMediaDocumentObject -> TODO()
        is MessageMediaWebPageObject -> InputMediaEmptyObject()
        is MessageMediaVenueObject -> InputMediaVenueObject(
            geo.toInputGeoPoint(),
            title,
            address,
            provider,
            venueId,
            venueType
        )
        is MessageMediaGameObject -> InputMediaGameObject(game.toInputGame())
        is MessageMediaInvoiceObject -> TODO()
        is MessageMediaGeoLiveObject -> InputMediaEmptyObject()
        is MessageMediaPollObject -> InputMediaPollObject(poll)
    }
}

fun GeoPointType.toInputGeoPoint() = when (this) {
    is GeoPointEmptyObject -> InputGeoPointEmptyObject()
    is GeoPointObject -> InputGeoPointObject(lat, long)
}

fun GameType.toInputGame() = when (this) {
    is GameObject -> InputGameIDObject(id, accessHash)
}
