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

import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.tl.*
import tk.hack5.telekat.core.updates.Skipped
import tk.hack5.telekat.core.updates.Update
import tk.hack5.telekat.core.updates.UpdateOrSkipped


interface Event

interface EventHandler<E : Event> {
    fun constructEvent(client: TelegramClient, update: UpdateOrSkipped): E? = when (update) {
        is Update -> constructEvent(client, update.update)
        is Skipped -> constructEvent(client, update.channelId)
    }

    fun constructEvent(client: TelegramClient, update: UpdateType): E? = throw NotImplementedError()
    fun constructEvent(client: TelegramClient, channelId: Int?): E? = throw NotImplementedError()

    companion object {
        val defaultHandlers = listOf(
            NewMessage,
            EditMessage,
            RawUpdate
        )
    }
}

object NewMessage : EventHandler<NewMessage.NewMessageEvent> {
    data class NewMessageEvent(
        override val client: TelegramClient,
        val originalUpdate: UpdateType,
        val originalMessage: MessageType,
        val out: Boolean,
        val mentioned: Boolean,
        val mediaUnread: Boolean,
        val silent: Boolean,
        val post: Boolean,
        val fromScheduled: Boolean,
        val legacy: Boolean,
        val editHide: Boolean,
        val id: Int,
        val fromId: Int?,
        val toId: PeerType,
        val fwdFrom: MessageFwdHeaderType?,
        val viaBotId: Int?,
        val replyToMsgId: Int?,
        val date: Int,
        val message: String,
        val media: MessageMediaType?,
        val replyMarkup: ReplyMarkupType?,
        val entities: List<MessageEntityType>,
        val views: Int?,
        val editDate: Int?,
        val postAuthor: String?,
        val groupedId: Long?,
        val restrictionReason: List<RestrictionReasonType>
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = fromId
        override val chat: PeerType
            get() = when (toId) {
                is PeerUserObject -> {
                    if (out) toId else PeerUserObject(fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
            }
    }

    override fun constructEvent(client: TelegramClient, update: UpdateType): NewMessageEvent? = when (update) {
        is UpdateNewMessageObject -> constructFromMessage(client, update, update.message)
        is UpdateNewChannelMessageObject -> constructFromMessage(client, update, update.message)
        else -> null
    }

    private fun constructFromMessage(
        client: TelegramClient,
        update: UpdateType,
        message: MessageType
    ): NewMessageEvent? = when (message) {
        is MessageEmptyObject -> null
        is MessageObject -> message.run {
            NewMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                fromScheduled,
                legacy,
                editHide,
                id,
                fromId,
                toId,
                fwdFrom,
                viaBotId,
                replyToMsgId,
                date,
                message.message,
                media,
                replyMarkup,
                entities ?: emptyList(),
                views,
                editDate,
                postAuthor,
                groupedId,
                restrictionReason ?: emptyList()
            )
        }
        is MessageServiceObject -> message.run {
            NewMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                false,
                legacy,
                false,
                id,
                fromId,
                toId,
                null,
                null,
                replyToMsgId,
                date,
                "",
                null,
                null,
                emptyList(),
                null,
                null,
                null,
                null,
                emptyList()
            )
        }
    }
}

object EditMessage : EventHandler<EditMessage.EditMessageEvent> {
    data class EditMessageEvent(
        override val client: TelegramClient,
        val originalUpdate: UpdateType,
        val originalMessage: MessageType,
        val out: Boolean,
        val mentioned: Boolean,
        val mediaUnread: Boolean,
        val silent: Boolean,
        val post: Boolean,
        val fromScheduled: Boolean,
        val legacy: Boolean,
        val editHide: Boolean,
        val id: Int,
        val fromId: Int?,
        val toId: PeerType,
        val fwdFrom: MessageFwdHeaderType?,
        val viaBotId: Int?,
        val replyToMsgId: Int?,
        val date: Int,
        val message: String,
        val media: MessageMediaType?,
        val replyMarkup: ReplyMarkupType?,
        val entities: List<MessageEntityType>,
        val views: Int?,
        val editDate: Int?,
        val postAuthor: String?,
        val groupedId: Long?,
        val restrictionReason: List<RestrictionReasonType>
    ) : Event, SenderGetter, ChatGetter {
        override val senderId: Int? get() = fromId
        override val chat: PeerType
            get() = when (toId) {
                is PeerUserObject -> {
                    if (out) toId else PeerUserObject(fromId!!)
                }
                is PeerChatObject -> toId
                is PeerChannelObject -> toId
            }
    }

    override fun constructEvent(client: TelegramClient, update: UpdateType): EditMessageEvent? = when (update) {
        is UpdateEditMessageObject -> constructFromMessage(client, update, update.message)
        is UpdateEditChannelMessageObject -> constructFromMessage(client, update, update.message)
        else -> null
    }

    private fun constructFromMessage(
        client: TelegramClient,
        update: UpdateType,
        message: MessageType
    ): EditMessageEvent? = when (message) {
        is MessageEmptyObject -> null
        is MessageObject -> message.run {
            EditMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                fromScheduled,
                legacy,
                editHide,
                id,
                fromId,
                toId,
                fwdFrom,
                viaBotId,
                replyToMsgId,
                date,
                message.message,
                media,
                replyMarkup,
                entities ?: emptyList(),
                views,
                editDate,
                postAuthor,
                groupedId,
                restrictionReason ?: emptyList()
            )
        }
        is MessageServiceObject -> message.run {
            EditMessageEvent(
                client,
                update,
                message,
                out,
                mentioned,
                mediaUnread,
                silent,
                post,
                false,
                legacy,
                false,
                id,
                fromId,
                toId,
                null,
                null,
                replyToMsgId,
                date,
                "",
                null,
                null,
                emptyList(),
                null,
                null,
                null,
                null,
                emptyList()
            )
        }
    }
}

object RawUpdate : EventHandler<RawUpdate.RawUpdateEvent> {
    data class RawUpdateEvent(val client: TelegramClient, val update: UpdateType) : Event

    override fun constructEvent(client: TelegramClient, update: UpdateType) = RawUpdateEvent(client, update)
}