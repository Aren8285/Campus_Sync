package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.ChatStatus
import com.campussync.app.data.model.ChatType
import com.campussync.app.data.model.LastMessage
import com.campussync.app.data.model.Message
import com.campussync.app.data.model.MessageType
import com.campussync.app.data.model.UserProfile
import com.campussync.app.data.model.isMutedBy
import com.campussync.app.domain.repository.ChatRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) : ChatRepository {

    private val chats = firestore.collection(CHATS)
    private val users = firestore.collection(USERS)
    private val follows = firestore.collection(FOLLOWS)
    private val pushQueue = firestore.collection(PUSH_QUEUE)

    private var cachedProfile: UserProfile? = null
    private var cachedProfileAt = 0L

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(AppException("You're signed out. Please log in again."))
            return@callbackFlow
        }

        val registration = chats
            .whereArrayContains("members", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(ChatRepository.CHAT_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't load your chats."))
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toChat() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    override fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val registration = chatRef(chatId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toFirestoreException("We couldn't open this chat."))
                return@addSnapshotListener
            }

            trySend(snapshot?.takeIf { it.exists() }?.toChat())
        }

        awaitClose { registration.remove() }
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val registration = chatRef(chatId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ChatRepository.MESSAGE_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't load these messages."))
                    return@addSnapshotListener
                }

                // A message written on this device has no server timestamp yet; sort
                // those last so the thread still reads oldest to newest.
                val messages = snapshot?.documents
                    ?.mapNotNull { it.toMessage(chatId) }
                    ?.sortedBy { it.createdAt ?: Long.MAX_VALUE }
                    .orEmpty()
                trySend(messages)
            }

        awaitClose { registration.remove() }
    }

    override fun observeTyping(chatId: String): Flow<Map<String, Long>> = callbackFlow {
        val registration = chatRef(chatId)
            .collection(TYPING)
            .limit(TYPING_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't show the typing indicator."))
                    return@addSnapshotListener
                }

                val typing = snapshot?.documents
                    ?.mapNotNull { document ->
                        val updatedAt = document.getTimestamp("updatedAt")?.toDate()?.time
                            ?: return@mapNotNull null
                        document.id to updatedAt
                    }
                    ?.toMap()
                    .orEmpty()
                trySend(typing)
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getStudents(limit: Int): List<UserProfile> = call(
        "We couldn't load your college's students."
    ) {
        val me = myProfile()
        users
            .whereEqualTo("collegeId", me.collegeId)
            .limit(limit.toLong())
            .get()
            .awaitResult()
            .documents
            .filter { it.id != me.uid && it.exists() }
            .map { it.toUserProfile() }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun openDirectChat(other: UserProfile): String = call(
        "We couldn't start this conversation."
    ) {
        val me = myProfile()
        if (other.uid.isBlank()) throw AppException("This student could not be found.")
        if (other.uid == me.uid) throw AppException("You can't message yourself.")
        // Chats are addressed by UID; refusing another college here stops a student
        // from carrying their college community into a conversation by accident.
        if (other.collegeId != me.collegeId) {
            throw AppException("You can only message students from your college.")
        }

        val reference = chats.document(directChatId(me.uid, other.uid))
        if (reference.get().awaitResult().exists()) return@call reference.id

        // Students who follow each other, in either direction, can message right
        // away. Everyone else goes through the message request flow.
        val connected = follows(me.uid, other.uid) || follows(other.uid, me.uid)

        reference.set(
            mapOf(
                "type" to ChatType.Direct.wireValue,
                "title" to "",
                "members" to listOf(me.uid, other.uid),
                "memberNames" to mapOf(
                    me.uid to me.name,
                    other.uid to other.name
                ),
                "admins" to emptyList<String>(),
                "createdBy" to me.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "readAt" to emptyMap<String, Any>(),
                "mutedBy" to emptyList<String>(),
                "status" to if (connected) {
                    ChatStatus.Accepted.wireValue
                } else {
                    ChatStatus.Request.wireValue
                },
                "requestedBy" to if (connected) "" else me.uid
            )
        ).awaitCompletion()

        reference.id
    }

    override suspend fun createGroup(title: String, members: List<UserProfile>): String = call(
        "We couldn't create this group."
    ) {
        val me = myProfile()
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) throw AppException("Give the group a name first.")
        if (trimmedTitle.length > ChatRepository.MAX_TITLE_LENGTH) {
            throw AppException(
                "Group names can be up to ${ChatRepository.MAX_TITLE_LENGTH} characters."
            )
        }

        val invited = members
            .filter { it.uid.isNotBlank() && it.uid != me.uid }
            .distinctBy { it.uid }
        if (invited.isEmpty()) throw AppException("Pick at least one classmate.")
        if (invited.any { it.collegeId != me.collegeId }) {
            throw AppException("Everyone in a group must be from your college.")
        }
        if (invited.size + 1 > ChatRepository.MAX_GROUP_MEMBERS) {
            throw AppException(
                "Groups can have up to ${ChatRepository.MAX_GROUP_MEMBERS} members."
            )
        }

        val memberNames = invited.associate { it.uid to it.name }.toMutableMap()
        memberNames[me.uid] = me.name

        chats.add(
            mapOf(
                "type" to ChatType.Group.wireValue,
                "title" to trimmedTitle,
                "members" to listOf(me.uid) + invited.map { it.uid },
                "memberNames" to memberNames,
                "admins" to listOf(me.uid),
                "createdBy" to me.uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "readAt" to emptyMap<String, Any>(),
                "mutedBy" to emptyList<String>(),
                "status" to ChatStatus.Accepted.wireValue,
                "requestedBy" to ""
            )
        ).awaitResult().id
    }

    override suspend fun sendMessage(chatId: String, text: String, imageUrl: String) = call(
        "We couldn't send your message. Please try again."
    ) {
        val me = myProfile()
        val trimmed = text.trim()
        val cleanUrl = imageUrl.trim()

        if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("https://")) {
            throw AppException("Image links must start with https://.")
        }
        if (trimmed.isEmpty() && cleanUrl.isEmpty()) {
            throw AppException("Write a message first.")
        }
        if (trimmed.length > ChatRepository.MAX_TEXT_LENGTH) {
            throw AppException(
                "Messages can be up to ${ChatRepository.MAX_TEXT_LENGTH} characters."
            )
        }

        val type = if (cleanUrl.isNotEmpty()) MessageType.Image else MessageType.Text
        val preview = when {
            trimmed.isNotEmpty() -> trimmed.take(ChatRepository.LAST_MESSAGE_PREVIEW)
            else -> "Photo"
        }

        val reference = chatRef(chatId)
        val messageRef = reference.collection(MESSAGES).document()
        val batch = firestore.batch()
        batch.set(
            messageRef,
            mapOf(
                "senderId" to me.uid,
                "senderName" to me.name,
                "type" to type.wireValue,
                "text" to trimmed,
                "imageUrl" to cleanUrl,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            reference,
            mapOf(
                "lastMessage" to mapOf(
                    "senderId" to me.uid,
                    "senderName" to me.name,
                    "type" to type.wireValue,
                    "text" to preview,
                    "at" to FieldValue.serverTimestamp()
                ),
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
        )
        batch.delete(reference.collection(TYPING).document(me.uid))
        batch.commit().awaitCompletion()

        // Queue a push for the relay that drains /pushQueue (see docs/PUSH.md).
        // Best-effort on purpose: the message is already committed, and a queue
        // problem must never turn a sent message into an error. The queue has a
        // TTL policy, so an unprocessed job disappears after a day.
        //
        // The job carries no notification text: its ID is the message ID, rules
        // require that message to exist and belong to this sender, and the relay
        // reads the copy from the message itself so it cannot be forged here.
        runCatching {
            pushQueue.document(messageRef.id).set(
                mapOf(
                    "chatId" to chatId,
                    "senderId" to me.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expireAt" to Timestamp(Date(System.currentTimeMillis() + PUSH_JOB_TTL_MS))
                )
            ).awaitCompletion()
        }
        Unit
    }

    override suspend fun setTyping(chatId: String, typing: Boolean) = call(
        "We couldn't update the typing indicator."
    ) {
        val me = myProfile()
        val reference = chatRef(chatId).collection(TYPING).document(me.uid)
        if (typing) {
            reference.set(mapOf("updatedAt" to FieldValue.serverTimestamp())).awaitCompletion()
        } else {
            reference.delete().awaitCompletion()
        }
    }

    override suspend fun markRead(chatId: String) = call(
        "We couldn't update your read receipts."
    ) {
        val me = myProfile()
        chatRef(chatId).update("readAt.${me.uid}", FieldValue.serverTimestamp())
            .awaitCompletion()
    }

    override suspend fun setMuted(chatId: String, muted: Boolean) = call(
        "We couldn't update your notification setting."
    ) {
        val me = myProfile()
        chatRef(chatId).update(
            "mutedBy",
            if (muted) FieldValue.arrayUnion(me.uid) else FieldValue.arrayRemove(me.uid)
        ).awaitCompletion()
    }

    override suspend fun renameGroup(chatId: String, title: String) = call(
        "We couldn't rename this group."
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw AppException("Give the group a name first.")
        if (trimmed.length > ChatRepository.MAX_TITLE_LENGTH) {
            throw AppException(
                "Group names can be up to ${ChatRepository.MAX_TITLE_LENGTH} characters."
            )
        }

        chatRef(chatId).update("title", trimmed).awaitCompletion()
    }

    override suspend fun addMembers(chatId: String, members: List<UserProfile>) = call(
        "We couldn't add these students."
    ) {
        val me = myProfile()
        val chat = readChat(chatId)
        val additions = members
            .filter { it.uid.isNotBlank() && it.uid !in chat.members }
            .distinctBy { it.uid }
        if (additions.isEmpty()) throw AppException("Those students are already in this group.")
        if (additions.any { it.collegeId != me.collegeId }) {
            throw AppException("Everyone in a group must be from your college.")
        }
        if (chat.members.size + additions.size > ChatRepository.MAX_GROUP_MEMBERS) {
            throw AppException(
                "Groups can have up to ${ChatRepository.MAX_GROUP_MEMBERS} members."
            )
        }

        val names = chat.memberNames.toMutableMap()
        additions.forEach { names[it.uid] = it.name }

        val reference = chatRef(chatId)
        reference.update(
            mapOf(
                "members" to chat.members + additions.map { it.uid },
                "memberNames" to names
            )
        ).awaitCompletion()
    }

    override suspend fun removeMember(chatId: String, uid: String) = call(
        "We couldn't remove this student."
    ) {
        val me = myProfile()
        if (uid == me.uid) throw AppException("Use \"Leave group\" to leave the chat.")
        val chat = readChat(chatId)
        if (uid !in chat.members) throw AppException("This student is not in the group.")

        val reference = chatRef(chatId)
        reference.update(
            mapOf(
                "members" to chat.members - uid,
                "memberNames" to (chat.memberNames - uid),
                "admins" to (chat.admins - uid)
            )
        ).awaitCompletion()
    }

    override suspend fun setAdmin(chatId: String, uid: String, admin: Boolean) = call(
        "We couldn't update this admin."
    ) {
        val chat = readChat(chatId)
        if (chat.type != ChatType.Group) throw AppException("Only groups have admins.")
        if (uid !in chat.members) throw AppException("This student is not in the group.")

        val admins = if (admin) (chat.admins + uid).distinct() else chat.admins - uid
        if (admins.isEmpty()) throw AppException("A group needs at least one admin.")

        chatRef(chatId).update("admins", admins).awaitCompletion()
    }

    override suspend fun leaveGroup(chatId: String) = call(
        "We couldn't remove you from this group."
    ) {
        val me = myProfile()
        val chat = readChat(chatId)
        if (chat.type != ChatType.Group) throw AppException("You can't leave a direct chat.")

        chatRef(chatId).update(
            mapOf(
                "members" to chat.members - me.uid,
                "memberNames" to (chat.memberNames - me.uid),
                "admins" to (chat.admins - me.uid)
            )
        ).awaitCompletion()
    }

    override suspend fun acceptRequest(chatId: String) = call(
        "We couldn't accept this request."
    ) {
        chatRef(chatId).update("status", ChatStatus.Accepted.wireValue).awaitCompletion()
    }

    override suspend fun declineRequest(chatId: String) = call(
        "We couldn't decline this request."
    ) {
        chatRef(chatId).delete().awaitCompletion()
    }

    override suspend fun shouldNotify(chatId: String): Boolean {
        val me = myProfile()
        val chat = runCatching { readChat(chatId) }.getOrNull() ?: return false
        return me.uid in chat.members && !chat.isMutedBy(me.uid)
    }

    // Firebase 25.1 deprecated the registration token APIs in favour of Firebase
    // Installation IDs. Tokens are still co-supported, and the FID replacement does
    // not hand the client an ID it can store without a project-wide flag, so the
    // token is what this app registers for a future trusted sender.
    @Suppress("DEPRECATION")
    override suspend fun registerPushToken() = call(
        "We couldn't register this device for notifications."
    ) {
        val me = myProfile()
        val token = messaging.token.awaitResult()
        storePushToken(me.uid, token)
    }

    override suspend fun savePushToken(token: String) = call(
        "We couldn't register this device for notifications."
    ) {
        val me = myProfile()
        storePushToken(me.uid, token)
    }

    private suspend fun storePushToken(uid: String, token: String) {
        if (token.isBlank()) return
        users.document(uid).collection(DEVICES).document(token).set(
            mapOf(
                "token" to token,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).awaitCompletion()
    }

    private fun chatRef(chatId: String): DocumentReference = chats.document(chatId)

    private suspend fun readChat(chatId: String): Chat {
        val snapshot = chatRef(chatId).get().awaitResult()
        return snapshot.toChat() ?: throw AppException("This chat no longer exists.")
    }

    private suspend fun follows(followerId: String, followeeId: String): Boolean =
        follows.document("${followerId}_$followeeId").get().awaitResult().exists()

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private suspend fun myProfile(): UserProfile {
        val uid = requireUid()
        val cached = cachedProfile
        if (cached != null && cached.uid == uid &&
            System.currentTimeMillis() - cachedProfileAt < PROFILE_CACHE_MS
        ) {
            return cached
        }

        val profile = users.document(uid).get().awaitResult().toUserProfile()
        if (profile.name.isBlank() || profile.collegeId.isBlank()) {
            throw AppException("Finish your profile before chatting.")
        }

        cachedProfile = profile
        cachedProfileAt = System.currentTimeMillis()
        return profile
    }

    private suspend fun <T> call(fallback: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException(fallback)
        }
    }

    private fun directChatId(firstUid: String, secondUid: String): String =
        "dm_" + listOf(firstUid, secondUid).sorted().joinToString("_")

    private fun DocumentSnapshot.toChat(): Chat? {
        if (!exists()) return null

        val names = (get("memberNames") as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                (key as? String)?.let { id -> id to (value as? String).orEmpty() }
            }
            ?.toMap()
            .orEmpty()

        val readMarkers = (get("readAt") as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                (key as? String)?.let { id ->
                    id to ((value as? Timestamp)?.toDate()?.time ?: 0L)
                }
            }
            ?.toMap()
            .orEmpty()

        val last = (get("lastMessage") as? Map<*, *>)?.let { map ->
            LastMessage(
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                type = MessageType.fromWire(map["type"] as? String),
                text = map["text"] as? String ?: "",
                at = (map["at"] as? Timestamp)?.toDate()?.time
            )
        }

        return Chat(
            id = id,
            type = ChatType.fromWire(getString("type")),
            title = getString("title").orEmpty(),
            members = (get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            memberNames = names,
            admins = (get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            status = ChatStatus.fromWire(getString("status")),
            requestedBy = getString("requestedBy").orEmpty(),
            lastMessage = last,
            lastMessageAt = getTimestamp("lastMessageAt")?.toDate()?.time,
            readAt = readMarkers,
            mutedBy = (get("mutedBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private fun DocumentSnapshot.toMessage(chatId: String): Message? {
        if (!exists()) return null
        return Message(
            id = id,
            chatId = chatId,
            senderId = getString("senderId").orEmpty(),
            senderName = getString("senderName").orEmpty(),
            type = MessageType.fromWire(getString("type")),
            text = getString("text").orEmpty(),
            imageUrl = getString("imageUrl").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private companion object {
        const val CHATS = "chats"
        const val MESSAGES = "messages"
        const val TYPING = "typing"
        const val USERS = "users"
        const val FOLLOWS = "follows"
        const val DEVICES = "devices"
        const val PUSH_QUEUE = "pushQueue"
        const val TYPING_LIMIT = 20
        const val PROFILE_CACHE_MS = 60_000L
        const val PUSH_JOB_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
