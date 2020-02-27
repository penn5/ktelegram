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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.state.UpdateState
import tk.hack5.telekat.core.tl.*
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
    val updates: Channel<UpdateType>
    suspend fun catchUp()
}

open class UpdateHandlerImpl(protected val updateState: UpdateState, val client: TelegramClient) : UpdateHandler {
    protected val updateStateLock = Mutex()
    protected val pendingUpdatesSeq = mutableMapOf<Int, Pair<CompletableJob, UpdatesType>>()
    protected val pendingUpdatesSeqLock = Mutex()
    protected val pendingUpdatesPts =
        mutableMapOf<Int?, MutableMap<Int, MutableList<Pair<CompletableJob, UpdateType>>>>()
    protected val pendingUpdatesPtsLock = Mutex()

    override val updates = Channel<UpdateType>(Channel.UNLIMITED)

    override suspend fun handleUpdates(update: TLObject<*>) = checkUpdates(update)

    override suspend fun getEntities(update: TLObject<*>) = AccessHashGetter().walk(update)!!

    override suspend fun catchUp() = updateStateLock.withLock { fetchUpdates() }

    protected suspend fun checkUpdates(update: TLObject<*>) {
        println(update)
        when (update) {
            is UpdatesCombinedObject -> {
                checkUpdateSeq(update, update.seqStart, update.seq)
            }
            is UpdatesObject -> {
                checkUpdateSeq(update, update.seq)
            }
            is UpdatesType -> dispatchUpdates(update)
        }
    }

    protected suspend fun checkUpdateSeq(updates: UpdatesType, seqStart: Int, seq: Int = seqStart) =
        updateStateLock.withLock {
            when {
                seqStart == 0 -> updateStateLock.withoutLock {
                    dispatchUpdates(updates)
                }
                updateState.seq + 1 == seqStart -> {
                    updateState.seq = seq
                    updateStateLock.withoutLock {
                        dispatchUpdates(updates)
                    }
                    pendingUpdatesSeqLock.withLock {
                        pendingUpdatesSeq.remove(seq)
                    }?.let {
                        updateStateLock.withoutLock {
                            checkUpdates(it.second)
                        }
                        it.first.complete()
                    }
                }
                updateState.seq + 1 < seqStart -> {
                    val updateHandledElsewhere = Job()
                    pendingUpdatesSeqLock.withLock {
                        pendingUpdatesSeq[seqStart - 1] = Pair(updateHandledElsewhere, updates)
                    }
                    val jobResult = updateStateLock.withoutLock {
                        withTimeoutOrNull(500) {
                            updateHandledElsewhere.join()
                        }
                    }
                    if (jobResult == null) {
                        // It wasn't fetched in 500ms, do it manually
                        fetchUpdates()
                    }
                }
            }
            Unit
        }

    protected suspend fun dispatchUpdates(updates: UpdatesType) {
        when (updates) {
            is UpdatesTooLongObject -> updateStateLock.withLock { fetchUpdates() }
            is UpdateShortMessageObject -> {
                updateDate(updates.date)
                handleSingleUpdate(
                    UpdateNewMessageObject(
                        MessageObject(
                            updates.out,
                            updates.mentioned,
                            updates.mediaUnread,
                            updates.silent,
                            false,
                            false,
                            false,
                            false,
                            updates.id,
                            if (updates.out) client.getInputMe().userId else updates.userId,
                            PeerUserObject(if (updates.out) updates.userId else client.getInputMe().userId),
                            updates.fwdFrom,
                            updates.viaBotId,
                            updates.replyToMsgId,
                            updates.date,
                            updates.message,
                            null,
                            null,
                            updates.entities
                        ), updates.pts, updates.ptsCount
                    )
                )
            }
            is UpdateShortChatMessageObject -> {
                updateDate(updates.date)
                handleSingleUpdate(
                    UpdateNewMessageObject(
                        MessageObject(
                            updates.out,
                            updates.mentioned,
                            updates.mediaUnread,
                            updates.silent,
                            false,
                            false,
                            false,
                            false,
                            updates.id,
                            updates.fromId,
                            PeerChatObject(updates.chatId),
                            updates.fwdFrom,
                            updates.viaBotId,
                            updates.replyToMsgId,
                            updates.date,
                            updates.message,
                            null,
                            null,
                            updates.entities
                        ), updates.pts, updates.ptsCount
                    )
                )
            }
            is UpdateShortObject -> {
                updateDate(updates.date)
                handleSingleUpdate(updates.update)
            }
            is UpdatesCombinedObject -> {
                updateDate(updates.date)
                if (updates.seq != 0)
                    updateState.seq = updates.seq
                coroutineScope {
                    updates.updates.forEach { launch { handleSingleUpdate(it) } }
                }
            }
            is UpdatesObject -> {
                updateDate(updates.date)
                if (updates.seq != 0)
                    updateState.seq = updates.seq
                coroutineScope {
                    updates.updates.forEach { launch { handleSingleUpdate(it) } }
                    println("all done")
                }
            }
            // Don't handle updateShortSentMessage because it has highly request-specific requirements
        }
    }

    protected suspend fun updateDate(date: Int) = updateStateLock.withLock {
        // Surely there is a better way? I'd rather avoid AtomicInt because it's hard to serialize
        if (date > updateState.date)
            updateState.date = date
    }

    protected suspend fun handleSingleUpdate(update: UpdateType) {
        when (update) {
            is UpdateNewMessageObject -> checkUpdatePts(update, null, pts = update.pts, ptsCount = update.ptsCount)
            is UpdateDeleteMessagesObject -> checkUpdatePts(update, null, pts = update.pts, ptsCount = update.ptsCount)
            is UpdateReadHistoryInboxObject -> checkUpdatePts(
                update,
                null,
                pts = update.pts,
                ptsCount = update.ptsCount
            )
            is UpdateReadHistoryOutboxObject -> checkUpdatePts(
                update,
                null,
                pts = update.pts,
                ptsCount = update.ptsCount
            )
            is UpdateWebPageObject -> checkUpdatePts(update, null, pts = update.pts, ptsCount = update.ptsCount)
            is UpdateReadMessagesContentsObject -> checkUpdatePts(
                update,
                null,
                pts = update.pts,
                ptsCount = update.ptsCount
            )
            is UpdateChannelTooLongObject -> updateStateLock.withLock { fetchChannelUpdates(update.channelId) }
            is UpdateNewChannelMessageObject -> checkUpdatePts(
                update,
                (update.message.toId as PeerChannelObject).channelId,
                update.pts,
                update.ptsCount
            )
            is UpdateReadChannelInboxObject -> checkUpdatePts(update, update.channelId, update.pts)
            is UpdateDeleteChannelMessagesObject -> checkUpdatePts(
                update,
                update.channelId,
                update.pts,
                update.ptsCount
            )
            is UpdateEditChannelMessageObject -> checkUpdatePts(
                update,
                (update.message.toId as PeerChannelObject).channelId,
                update.pts,
                update.ptsCount
            )
            is UpdateEditMessageObject -> checkUpdatePts(update, null, pts = update.pts, ptsCount = update.ptsCount)
            //is UpdatePtsChangedObject -> updateState.pts[null] = getState().pts
            is UpdateChannelWebPageObject -> checkUpdatePts(update, update.channelId, update.pts, update.ptsCount)
            else -> dispatchUpdate(update)
        }
    }

    protected suspend fun fetchUpdates() {
        require(updateStateLock.isLocked) { "updateStateLock is not locked during fetch - this can lead to concurrency errors" }
        val updates = mutableListOf<UpdateType>()
        var tmpState: Updates_StateObject? = null
        loop@ while (true) {
            val difference = client(
                Updates_GetDifferenceRequest(
                    tmpState?.pts ?: updateState.pts[null]!!,
                    null,
                    tmpState?.date ?: updateState.date,
                    tmpState?.qts ?: updateState.qts
                )
            )
            println("difference = $difference")
            when (difference) {
                is Updates_DifferenceObject -> {
                    println(
                        "dispatching updates: ${updates + difference.otherUpdates + generateUpdates(
                            difference.newMessages,
                            difference.newEncryptedMessages
                        )}"
                    )
                    val state = difference.state as Updates_StateObject
                    updateStateLock.withoutLock {
                        dispatchUpdates(
                            UpdatesObject(
                                updates + difference.otherUpdates + generateUpdates(
                                    difference.newMessages,
                                    difference.newEncryptedMessages
                                ),
                                difference.users,
                                difference.chats,
                                state.date,
                                state.seq
                            )
                        )
                    }
                    println("fetched updates ${updateState.seq} -> ${state.seq}")
                    require(updateState.date == state.date)
                    require(updateState.pts[null] == state.pts)
                    require(updateState.qts == state.qts)
                    require(updateState.seq == state.seq)
//                    updateState.date = state.date
//                    updateState.pts[null] = state.pts
//                    updateState.qts = state.qts
//                    updateState.seq = state.seq
                    break@loop
                }
                is Updates_DifferenceSliceObject -> {
                    tmpState = difference.intermediateState as Updates_StateObject
                    updates += difference.otherUpdates + generateUpdates(
                        difference.newMessages,
                        difference.newEncryptedMessages
                    )
                }
                is Updates_DifferenceEmptyObject -> break@loop
                is Updates_DifferenceTooLongObject -> break@loop
            }
        }
    }

    protected fun generateUpdates(
        newMessages: List<MessageType>,
        newEncryptedMessages: List<EncryptedMessageType>
    ): List<UpdateType> = newMessages.map {
        UpdateNewMessageObject(
            it,
            0,
            0
        )
    } + newEncryptedMessages.map { UpdateNewEncryptedMessageObject(it, 0) }

    protected suspend fun fetchChannelUpdates(channelId: Int) {
        require(updateStateLock.isLocked) { "updateStateLock is not locked during channel fetch - this can lead to concurrency errors" }
        val pts = updateState.pts[channelId]
        val inputChannel = InputChannelObject(channelId, client.getAccessHash(ObjectType.CHANNEL, channelId)!!)
        if (pts == null)
        // Fetch new pts
            updateState.pts[channelId] =
                ((client(Channels_GetFullChannelRequest(inputChannel)) as Messages_ChatFullObject).fullChat as ChannelFullObject).pts
        else {
            // Fetch the update, meaning we get access to all entities for entity caching.
            // TODO make this disableable this when entity cache disablement is supported
            val result = client(
                Updates_GetChannelDifferenceRequest(
                    true,
                    inputChannel,
                    ChannelMessagesFilterEmptyObject(),
                    pts,
                    100
                )
            )
            when (result) {
                is Updates_ChannelDifferenceEmptyObject -> {
                }
                is Updates_ChannelDifferenceObject -> {
                    updateState.pts[channelId] = result.pts
                    if (!result.final) {
                        fetchChannelUpdates(channelId)
                    }
                }
                is Updates_ChannelDifferenceTooLongObject -> {
                    updateState.pts[channelId] = (result.dialog as DialogObject).pts!!
                    fetchChannelUpdates(channelId)
                }
            }
        }
    }

    protected suspend fun checkUpdatePts(update: UpdateType, channelId: Int?, pts: Int, ptsCount: Int = 0) =
        updateStateLock.withLock {
            val localPts = updateState.pts[channelId]
            println("current stats")
            println(updateState.pts[channelId])
            println(pendingUpdatesPts[channelId])
            println(pendingUpdatesPts)
            when {
                localPts == null || localPts + ptsCount == pts -> {
                    if (ptsCount > 0)
                        updateState.pts[channelId] = pts
                    dispatchUpdate(update)
                    pendingUpdatesPtsLock.withLock {
                        pendingUpdatesPts[channelId]?.remove(pts)
                    }?.forEach {
                        handleSingleUpdate(it.second)
                        it.first.complete()
                    }
                }
                localPts + ptsCount > pts -> return
                localPts + ptsCount < pts -> {
                    println("PTS MISSING!!!!!!!!!!!!!!!!!!!!!! $localPts $pts $ptsCount $update")
                    val updateHandledElsewhere = Job()
                    pendingUpdatesPtsLock.withLock {
                        pendingUpdatesPts.getOrPut(channelId, { mutableMapOf() })
                            .getOrPut(pts - ptsCount, { mutableListOf() }).add(Pair(updateHandledElsewhere, update))
                    }
                    val jobResult = updateStateLock.withoutLock {
                        withTimeoutOrNull(500) {
                            updateHandledElsewhere.join()
                        }
                    }
                    if (jobResult == null) {
                        if (channelId == null)
                            fetchUpdates()
                        else {
                            fetchChannelUpdates(channelId)
                            println("FETCH COMPLETE FOR PTS MISSING $update")
                            println(updateState.pts[channelId])
                            println(pendingUpdatesPts[channelId])
                            println(pendingUpdatesPts)
                        }
                    }
                }
            }
            Unit
        }

    protected suspend fun dispatchUpdate(update: UpdateType) = updateStateLock.withoutLock {
        updates.send(update)
    }
}

private val MessageType.toId: PeerType?
    get() = when (this) {
        is MessageEmptyObject -> null
        is MessageObject -> toId
        is MessageServiceObject -> toId
    }

private suspend inline fun <T> Mutex.withoutLock(owner: Any? = null, action: () -> T): T {
    unlock(owner)
    try {
        return action()
    } finally {
        lock(owner)
    }
}