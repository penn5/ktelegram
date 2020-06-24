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

package tk.hack5.telekat.core.updates

import com.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.state.UpdateState
import tk.hack5.telekat.core.tl.*
import tk.hack5.telekat.core.utils.BaseActor
import tk.hack5.telekat.core.utils.TLWalker

enum class ObjectType {
    USER,
    CHANNEL,
    PHOTO,
    ENCRYPTED_FILE_LOCATION,
    DOCUMENT_FILE_LOCATION,
    SECURE_FILE_LOCATION,
    PHOTO_FILE_LOCATION,
    PHOTO_LEGACY_FILE_LOCATION,
    WALLPAPER,
    ENCRYPTED_CHAT,
    ENCRYPTED_FILE,
    DOCUMENT,
    BOT_INLINE,
    THEME,
    SECURE_FILE

}

class AccessHashGetter : TLWalker<MutableMap<String, MutableMap<Long, Long>>>() {
    override val result = mutableMapOf<String, MutableMap<Long, Long>>()

    override fun handle(key: String, value: TLObject<*>?): Boolean {
        val objectType: ObjectType
        val id: Long
        val accessHash: Long
        when (value) {
            is InputPeerUserObject -> {
                objectType = ObjectType.USER
                id = value.userId.toLong()
                accessHash = value.accessHash
            }
            is InputPeerChannelObject -> {
                objectType = ObjectType.CHANNEL
                id = value.channelId.toLong()
                accessHash = value.accessHash
            }
            is InputUserObject -> {
                objectType = ObjectType.USER
                id = value.userId.toLong()
                accessHash = value.accessHash
            }
            is InputPhotoObject -> {
                objectType = ObjectType.PHOTO
                id = value.id
                accessHash = value.accessHash
            }
            is InputEncryptedFileLocationObject -> {
                objectType = ObjectType.ENCRYPTED_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputDocumentFileLocationObject -> {
                objectType = ObjectType.DOCUMENT_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputSecureFileLocationObject -> {
                objectType = ObjectType.SECURE_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputPhotoFileLocationObject -> {
                objectType = ObjectType.PHOTO_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is InputPhotoLegacyFileLocationObject -> {
                objectType = ObjectType.PHOTO_LEGACY_FILE_LOCATION
                id = value.id
                accessHash = value.accessHash
            }
            is UserObject -> {
                objectType = ObjectType.USER
                id = value.id.toLong()
                accessHash = value.accessHash ?: return true
            }
            is ChannelObject -> {
                objectType = ObjectType.CHANNEL
                id = value.id.toLong()
                accessHash = value.accessHash ?: return true
            }
            is ChannelForbiddenObject -> {
                objectType = ObjectType.CHANNEL
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is PhotoObject -> {
                objectType = ObjectType.PHOTO
                id = value.id
                accessHash = value.accessHash
            }
            is EncryptedChatWaitingObject -> {
                objectType = ObjectType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is EncryptedChatRequestedObject -> {
                objectType = ObjectType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is EncryptedChatObject -> {
                objectType = ObjectType.ENCRYPTED_CHAT
                id = value.id.toLong()
                accessHash = value.accessHash
            }
            is InputEncryptedChatObject -> {
                objectType = ObjectType.ENCRYPTED_CHAT
                id = value.chatId.toLong()
                accessHash = value.accessHash
            }
            is EncryptedFileObject -> {
                objectType = ObjectType.ENCRYPTED_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputEncryptedFileObject -> {
                objectType = ObjectType.ENCRYPTED_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputDocumentObject -> {
                objectType = ObjectType.DOCUMENT
                id = value.id
                accessHash = value.accessHash
            }
            is DocumentObject -> {
                objectType = ObjectType.DOCUMENT
                id = value.id
                accessHash = value.accessHash
            }
            is InputChannelObject -> {
                objectType = ObjectType.CHANNEL
                id = value.channelId.toLong()
                accessHash = value.accessHash
            }
            is InputBotInlineMessageIDObject -> {
                objectType = ObjectType.BOT_INLINE
                id = value.id
                accessHash = value.accessHash
            }
            is InputSecureFileObject -> {
                objectType = ObjectType.SECURE_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is SecureFileObject -> {
                objectType = ObjectType.SECURE_FILE
                id = value.id
                accessHash = value.accessHash
            }
            is InputWallPaperObject -> {
                objectType = ObjectType.WALLPAPER
                id = value.id
                accessHash = value.accessHash
            }
            is InputThemeObject -> {
                objectType = ObjectType.THEME
                id = value.id
                accessHash = value.accessHash
            }
            is ThemeObject -> {
                objectType = ObjectType.THEME
                id = value.id
                accessHash = value.accessHash
            }

            else -> return true
        }
        result.getOrPut(objectType.toString(), { mutableMapOf() })[id] = accessHash
        return true
    }
}

interface UpdateHandler {
    suspend fun getEntities(update: TLObject<*>): MutableMap<String, MutableMap<Long, Long>>
    suspend fun handleUpdates(update: TLObject<*>)
    val updates: Channel<UpdateOrSkipped>
    suspend fun catchUp()
}

open class UpdateHandlerImpl(
    protected val updateState: UpdateState,
    val client: TelegramClient,
    protected val maxDifference: Int? = null,
    val maxChannelDifference: Int = 100
) : BaseActor(), UpdateHandler {
    val pendingUpdatesSeq = mutableMapOf<Int, CompletableJob>()
    val pendingUpdatesPts = mutableMapOf<Pair<Int?, Int>, CompletableJob>()

    override val updates = Channel<UpdateOrSkipped>(Channel.UNLIMITED)

    override suspend fun handleUpdates(update: TLObject<*>) {
        if (update is UpdatesType) handleUpdates(update)
    }

    override suspend fun getEntities(update: TLObject<*>) = AccessHashGetter().walk(update)!!

    suspend fun handleUpdates(updates: UpdatesType) {
        val innerUpdates = when (updates) {
            is UpdatesTooLongObject -> {
                fetchUpdates()
                return
            }
            is UpdateShortMessageObject -> listOf(
                UpdateNewMessageObject(
                    MessageObject(
                        out = updates.out,
                        mentioned = updates.mentioned,
                        mediaUnread = updates.mediaUnread,
                        silent = updates.silent,
                        post = false,
                        fromScheduled = false,
                        legacy = false,
                        editHide = false,
                        id = updates.id,
                        fromId = if (updates.out) client.getInputMe().userId else updates.userId,
                        toId = PeerUserObject(if (updates.out) updates.userId else client.getInputMe().userId),
                        fwdFrom = updates.fwdFrom,
                        viaBotId = updates.viaBotId,
                        replyToMsgId = updates.replyToMsgId,
                        date = updates.date,
                        message = updates.message,
                        media = null,
                        replyMarkup = null,
                        entities = updates.entities
                    ), updates.pts, updates.ptsCount
                )
            )
            is UpdateShortChatMessageObject -> listOf(
                UpdateNewMessageObject(
                    MessageObject(
                        out = updates.out,
                        mentioned = updates.mentioned,
                        mediaUnread = updates.mediaUnread,
                        silent = updates.silent,
                        post = false,
                        fromScheduled = false,
                        legacy = false,
                        editHide = false,
                        id = updates.id,
                        fromId = updates.fromId,
                        toId = PeerChatObject(updates.chatId),
                        fwdFrom = updates.fwdFrom,
                        viaBotId = updates.viaBotId,
                        replyToMsgId = updates.replyToMsgId,
                        date = updates.date,
                        message = updates.message,
                        media = null,
                        replyMarkup = null,
                        entities = updates.entities
                    ), updates.pts, updates.ptsCount
                )
            )
            is UpdateShortObject -> listOf(updates.update)
            is UpdatesCombinedObject -> updates.updates
            is UpdatesObject -> updates.updates
            is UpdateShortSentMessageObject -> return // handled by rpc caller
        }
        updates.date?.let { checkDate(it) }
        val (hasPts, hasNoPts) = innerUpdates.partition { it.pts != null }
        for (update in hasPts) {
            val pts = update.pts!!
            val ptsCount = update.ptsCount
            if (update is UpdateChannelTooLongObject) {
                fetchChannelUpdates(update.channelId)
                return
            }
            val (applicablePts, job) = act {
                val localPts = updateState.pts[update.channelId]
                val applicablePts = pts - ptsCount!!

                val job = when {
                    (ptsCount == 0 && pts >= localPts?.minus(1) ?: 0)
                            || applicablePts == 0 -> {
                        // update doesn't need to change the pts
                        dispatchUpdate(update)
                        null
                    }
                    applicablePts == localPts || localPts == null -> {
                        dispatchUpdate(update)
                        updateState.pts[update.channelId] = pts
                        null
                    }
                    applicablePts < localPts -> {
                        Napier.d("Duplicate update $update (localPts=$localPts)", tag = tag)
                        null
                    }
                    else -> {
                        //require(!preventGapFilling) { "Gap found in gap refill($applicablePts, $localPts, ${update.channelId})" }
                        val job = Job()
                        pendingUpdatesPts[update.channelId to applicablePts] = job
                        job
                    }
                }
                Pair(applicablePts, job)
            }
            job?.let {
                val join = withTimeoutOrNull(500) {
                    it.join()
                }
                pendingUpdatesPts.remove(update.channelId to applicablePts)
                if (join == null) {
                    if (update.channelId != null) {
                        fetchChannelUpdates(update.channelId!!)
                    } else {
                        fetchUpdates()
                    }
                    return // server will resend this update too
                }
                dispatchUpdate(update)
                act {
                    updateState.pts[update.channelId] = pts
                    pendingUpdatesPts[update.channelId to pts]?.complete()
                }
            }
        }

        val (localSeq, applicableSeq, job) = act {
            val applicableSeq = updates.seqStart?.minus(1)
            val localSeq = updateState.seq
            val job = when {
                applicableSeq == null || applicableSeq == -1 -> {
                    // update order doesn't matter
                    for (update in hasNoPts) {
                        dispatchUpdate(update)
                    }
                    null
                }
                applicableSeq == localSeq -> {
                    for (update in hasNoPts) {
                        dispatchUpdate(update)
                    }
                    updateState.seq = updates.seq!!
                    updates.date?.let { checkDate(it) }
                    null
                }
                applicableSeq < localSeq -> {
                    Napier.d("Duplicate updates $updates (localSeq=$localSeq)", tag = tag)
                    null
                }
                else -> {
                    //require(!preventGapFilling) { "Gap found in gap refill($applicableSeq, $localSeq)" }
                    val job = Job()
                    pendingUpdatesSeq[applicableSeq] = job
                    job
                }
            }
            Triple(localSeq, applicableSeq, job)
        }
        job?.let {
            Napier.d("Waiting for update with seq=$applicableSeq (current=$localSeq, updates=$updates)", tag = tag)
            val join = withTimeoutOrNull(500) {
                it.join()
            }
            if (join == null) {
                act {
                    pendingUpdatesSeq.remove(applicableSeq)
                }
                fetchUpdates()
                return // server will resend this update too
            }
            for (update in hasNoPts) {
                dispatchUpdate(update)
            }
            act {
                updateState.seq = applicableSeq!! + 1
                pendingUpdatesSeq[applicableSeq + 1]?.complete()
            }
        }
    }

    override suspend fun catchUp() = fetchUpdates()

    protected suspend fun checkDate(date: Int) = act {
        if (date > updateState.date)
            updateState.date = date
    }

    protected fun dispatchUpdate(update: UpdateType) {
        require(updates.offer(Update(update))) { "Failed to offer update" }
    }

    protected suspend fun fetchUpdates() {
        val updates = mutableListOf<UpdateType>()
        var tmpState: Updates_StateObject? = null
        loop@ while (true) {
            val difference = client(
                act {
                    Updates_GetDifferenceRequest(
                        tmpState?.pts ?: updateState.pts[null]!!,
                        maxDifference,
                        tmpState?.date ?: updateState.date,
                        tmpState?.qts ?: updateState.qts
                    )
                }
            )
            when (difference) {
                is Updates_DifferenceObject -> {
                    val state = difference.state as Updates_StateObject
                    handleUpdates(
                        UpdatesObject(
                            updates + difference.otherUpdates + generateUpdates(
                                difference.newMessages,
                                difference.newEncryptedMessages,
                                ::UpdateNewMessageObject
                            ),
                            difference.users,
                            difference.chats,
                            state.date,
                            state.seq
                        )
                    )
                    break@loop
                }
                is Updates_DifferenceSliceObject -> {
                    tmpState = difference.intermediateState as Updates_StateObject
                    updates += difference.otherUpdates + generateUpdates(
                        difference.newMessages,
                        difference.newEncryptedMessages,
                        ::UpdateNewMessageObject
                    )
                }
                is Updates_DifferenceEmptyObject -> break@loop
                is Updates_DifferenceTooLongObject -> {
                    act {
                        require(this.updates.offer(Skipped(null))) { "Failed to offer drop message" }
                        updateState.pts[null] = difference.pts
                    }
                    break@loop
                }
            }
        }
    }

    protected suspend fun fetchChannelUpdates(channelId: Int) {
        val inputChannel = InputChannelObject(channelId, client.getAccessHash(ObjectType.CHANNEL, channelId)!!)
        val pts = act {
            val pts = updateState.pts[channelId]
            if (pts == null) {
                updateState.pts[channelId] =
                    ((client(Channels_GetFullChannelRequest(inputChannel)) as Messages_ChatFullObject).fullChat as ChannelFullObject).pts
                null
            } else {
                pts
            }
        } ?: return
        val result = client(
            Updates_GetChannelDifferenceRequest(
                true,
                inputChannel,
                ChannelMessagesFilterEmptyObject(),
                pts,
                maxChannelDifference
            )
        )
        when (result) {
            is Updates_ChannelDifferenceEmptyObject -> {
            }
            is Updates_ChannelDifferenceObject -> {
                handleUpdates(
                    UpdatesObject(
                        result.otherUpdates + generateUpdates(
                            result.newMessages,
                            listOf(),
                            ::UpdateNewChannelMessageObject
                        ),
                        result.users,
                        result.chats,
                        result.pts,
                        0
                    )
                )
                act { updateState.pts[channelId] = result.pts } // updates sent in the difference may not have a pts
                if (!result.final) {
                    fetchChannelUpdates(channelId)
                }
            }
            is Updates_ChannelDifferenceTooLongObject -> act {
                updates.offer(Skipped(channelId))
                updateState.pts[channelId] = (result.dialog as DialogObject).pts!!
            }
        }
    }

    protected fun generateUpdates(
        newMessages: List<MessageType>,
        newEncryptedMessages: List<EncryptedMessageType>,
        constructor: (MessageType, Int, Int, Boolean) -> UpdateType
    ): List<UpdateType> =
        newMessages.map {
            constructor(
                it,
                0,
                0,
                false
            )
        } + newEncryptedMessages.map { UpdateNewEncryptedMessageObject(it, 0) }


    private val UpdatesType.date
        get() = when (this) {
            is UpdateShortMessageObject -> date
            is UpdateShortChatMessageObject -> date
            is UpdateShortObject -> date
            is UpdatesCombinedObject -> date
            is UpdatesObject -> date
            is UpdateShortSentMessageObject -> date
            else -> null
        }
    private val UpdatesType.seq
        get() = when (this) {
            is UpdatesCombinedObject -> seq
            is UpdatesObject -> seq
            else -> null
        }
    private val UpdatesType.seqStart
        get() = when (this) {
            is UpdatesCombinedObject -> seqStart
            is UpdatesObject -> seq
            else -> null
        }
    private val UpdateType.channelId
        get() = when (this) {
            is UpdateNewChannelMessageObject -> (message.toId as PeerChannelObject).channelId
            is UpdateChannelTooLongObject -> channelId
            is UpdateReadChannelInboxObject -> channelId
            is UpdateDeleteChannelMessagesObject -> channelId
            is UpdateEditChannelMessageObject -> (message.toId as PeerChannelObject).channelId
            is UpdateChannelWebPageObject -> channelId
            else -> null
        }
    private val UpdateType.pts
        get() = when (this) {
            is UpdateNewChannelMessageObject -> pts
            is UpdateNewMessageObject -> pts
            is UpdateDeleteMessagesObject -> pts
            is UpdateReadHistoryInboxObject -> pts
            is UpdateReadHistoryOutboxObject -> pts
            is UpdateWebPageObject -> pts
            is UpdateReadMessagesContentsObject -> pts
            is UpdateChannelTooLongObject -> pts
            is UpdateReadChannelInboxObject -> pts - 1 // this one is messed up, but -1 seems to fix it
            is UpdateDeleteChannelMessagesObject -> pts
            is UpdateEditChannelMessageObject -> pts
            is UpdateEditMessageObject -> pts
            is UpdateChannelWebPageObject -> pts
            is UpdateFolderPeersObject -> pts
            else -> null
        }
    private val UpdateType.ptsCount
        get() = when (this) {
            is UpdateNewChannelMessageObject -> ptsCount
            is UpdateNewMessageObject -> ptsCount
            is UpdateDeleteMessagesObject -> ptsCount
            is UpdateReadHistoryInboxObject -> ptsCount
            is UpdateReadHistoryOutboxObject -> ptsCount
            is UpdateWebPageObject -> ptsCount
            is UpdateReadMessagesContentsObject -> ptsCount
            is UpdateChannelTooLongObject -> null
            is UpdateReadChannelInboxObject -> 0
            is UpdateDeleteChannelMessagesObject -> ptsCount
            is UpdateEditChannelMessageObject -> ptsCount
            is UpdateEditMessageObject -> ptsCount
            is UpdateChannelWebPageObject -> ptsCount
            is UpdateFolderPeersObject -> ptsCount
            else -> null
        }
}

sealed class UpdateOrSkipped(open val update: UpdateType?)
data class Update(override val update: UpdateType) : UpdateOrSkipped(update)
data class Skipped(val channelId: Int?) : UpdateOrSkipped(null)

private val MessageType.toId: PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }

private const val tag = "UpdateHandler"