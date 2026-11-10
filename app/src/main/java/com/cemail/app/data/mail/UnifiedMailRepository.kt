package com.cemail.app.data.mail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * All the accounts seen as one mailbox (the unified inbox). Message ids are unique across accounts, so
 * every action is simply offered to each account's repository, and only the one that owns the message acts.
 * New messages (drafts, sent mail) go to the first account.
 */
class UnifiedMailRepository(private val accounts: List<MailRepository>, scope: CoroutineScope) : MailRepository {
    init {
        require(accounts.isNotEmpty())
    }

    override val mails: Flow<List<Mail>> = combine(accounts.map { it.mails }) { lists -> lists.flatMap { it } }

    /** Labels belong to one account: they are shown when that account is selected, not in the unified view. */
    override val labels: Flow<List<Label>> = flowOf(emptyList())
    override suspend fun userLabels(accountId: String): List<Label> = accounts.flatMap { it.userLabels(accountId) }

    override val syncing: StateFlow<Boolean> = combine(accounts.map { it.syncing }) { all -> all.any { it } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) = accounts.forEach { it.update(ids, transform) }
    override fun restore(previous: List<Mail>) = accounts.forEach { it.restore(previous) }
    override fun snapshot(ids: Set<Long>): List<Mail> = accounts.flatMap { it.snapshot(ids) }
    override fun add(mail: Mail) = accounts.first().add(mail)
    override fun send(mail: Mail) = accounts.first().send(mail)
    override fun nextId(): Long = accounts.first().nextId()
    override fun remove(id: Long) = accounts.forEach { it.remove(id) }

    override suspend fun refresh(quick: Boolean): Boolean = coroutineScope {
        accounts.map { async { it.refresh(quick) } }.awaitAll().all { it }
    }

    override suspend fun attachments(id: Long): List<com.cemail.app.data.gmail.AttachmentInfo> {
        for (account in accounts) account.attachments(id).takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    override suspend fun attachmentFile(id: Long, attachment: com.cemail.app.data.gmail.AttachmentInfo): java.io.File? {
        for (account in accounts) account.attachmentFile(id, attachment)?.let { return it }
        return null
    }

    override suspend fun emlFile(id: Long): java.io.File? {
        for (account in accounts) account.emlFile(id)?.let { return it }
        return null
    }

    override suspend fun replyInfo(id: Long): Pair<com.cemail.app.data.gmail.ReplyInfo, String?>? {
        for (account in accounts) account.replyInfo(id)?.let { return it }
        return null
    }

    override suspend fun isMuted(id: Long): Boolean = accounts.any { it.isMuted(id) }
    override fun setMuted(id: Long, muted: Boolean) = accounts.forEach { it.setMuted(id, muted) }
    override suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo? {
        for (account in accounts) account.unsubscribeInfo(id)?.let { return it }
        return null
    }
    override suspend fun searchIds(query: String): Set<Long> = accounts.flatMap { it.searchIds(query) }.toSet()
    override suspend fun schedule(mail: Mail, at: Long): Long = accounts.first().schedule(mail, at)
    override fun cancelScheduled(id: Long) = accounts.forEach { it.cancelScheduled(id) }
    override suspend fun deliver(id: Long): Boolean = accounts.any { it.deliver(id) }

    override suspend fun emptyFolder(folder: Folder): Boolean = coroutineScope {
        accounts.map { async { it.emptyFolder(folder) } }.awaitAll().all { it }
    }

    override suspend fun full(id: Long): Mail? {
        for (account in accounts) account.full(id)?.let { return it }
        return null
    }

    override suspend fun inlineImages(id: Long): Map<String, String> {
        for (account in accounts) {
            val images = account.inlineImages(id)
            if (images.isNotEmpty()) return images
        }
        return emptyMap()
    }
}
