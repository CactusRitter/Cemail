package com.cemail.app.data.mail

import com.cemail.app.AppLog
import com.cemail.app.data.db.MessageDao

/** Puts a message in the Outbox: a row with the text, and its attachments in files. Returns the row's id. */
internal suspend fun MessageDao.scheduleOutbox(accountId: String, mail: Mail, at: Long): Long {
    val id = insert(
        mail.toEntity(accountId, remoteId = null)
            .copy(localId = 0, folder = Folder.OUTBOX.name, timeMillis = at, sendAt = at, read = true),
    )
    Outbox.save(id, mail.outgoing, mail.outgoingImages)
    return id
}

internal suspend fun MessageDao.cancelOutbox(id: Long) {
    delete(id)
    Outbox.delete(id)
}

/**
 * Sends a message of the Outbox with [transmit] (the account's own way of sending). While it goes out it shows in Sent.
 * If it fails it is tried again every couple of minutes (there may be no connection) and, after half an hour, it goes
 * back to Drafts so the text is never lost. Returns true if it was sent.
 */
internal suspend fun deliverOutbox(dao: MessageDao, accountId: String, id: Long, transmit: suspend (Mail) -> Unit): Boolean {
    val row = dao.get(id)?.takeIf { it.accountId == accountId && it.folder == Folder.OUTBOX.name } ?: return false
    val (attachments, images) = Outbox.load(id)
    val mail = row.toMail().copy(
        outgoing = attachments, outgoingImages = images, outgoingHtml = row.bodyHtml?.takeIf { it.isNotBlank() },
    )
    dao.updateFolder(id, Folder.SENT.name)
    return try {
        transmit(mail)
        dao.delete(id)
        Outbox.delete(id)
        AppLog.i("outbox", "sent a message from the Outbox, ${attachments.size} attachment(s)")
        true
    } catch (e: Exception) {
        AppLog.e("outbox", "could not send from the Outbox", e)
        if (System.currentTimeMillis() > row.timeMillis + Outbox.GIVE_UP_MILLIS) {
            dao.updateFolder(id, Folder.DRAFTS.name)
            Outbox.delete(id)
        } else {
            dao.updateFolder(id, Folder.OUTBOX.name)
            dao.setSendAt(id, System.currentTimeMillis() + Outbox.RETRY_MILLIS)
        }
        false
    }
}
