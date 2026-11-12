package com.cemail.app.data.imap

import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.ParsedMessage
import com.sun.mail.imap.IMAPFolder
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

/** What a mailbox folder is for. Cemail syncs the first five; the others are ignored. */
enum class Role { INBOX, SENT, DRAFTS, TRASH, ARCHIVE, JUNK, OTHER }

class RemoteFolder(val path: String, val role: Role)

/** A message of the server's window with what changes often (its flags) and the message itself, fetched on demand. */
class RemoteMessage(val uid: Long, val seen: Boolean, val flagged: Boolean, val message: Message)

/**
 * The IMAP and SMTP side of an account, with Jakarta Mail for Android. Everything here blocks (network): callers run
 * it on Dispatchers.IO. A new connection is opened for each piece of work and closed at the end.
 */
class ImapClient(private val config: ImapConfig) {
    private val imapProtocol = if (config.imapSecurity == Security.SSL) "imaps" else "imap"
    private val smtpProtocol = if (config.smtpSecurity == Security.SSL) "smtps" else "smtp"

    private val session: Session by lazy {
        val p = Properties()
        for (proto in listOf("imap", "imaps")) {
            p["mail.$proto.connectiontimeout"] = "20000"
            p["mail.$proto.timeout"] = "30000"
            p["mail.$proto.ssl.checkserveridentity"] = "true"
            p["mail.$proto.partialfetch"] = "true"
        }
        if (config.imapSecurity == Security.STARTTLS) {
            p["mail.imap.starttls.enable"] = "true"
            p["mail.imap.starttls.required"] = "true"
        }
        for (proto in listOf("smtp", "smtps")) {
            p["mail.$proto.connectiontimeout"] = "20000"
            p["mail.$proto.timeout"] = "30000"
            p["mail.$proto.ssl.checkserveridentity"] = "true"
            p["mail.$proto.auth"] = "true"
        }
        if (config.smtpSecurity == Security.STARTTLS) {
            p["mail.smtp.starttls.enable"] = "true"
            p["mail.smtp.starttls.required"] = "true"
        }
        Session.getInstance(p)
    }

    fun <T> withStore(block: (Store) -> T): T {
        val store = session.getStore(imapProtocol)
        store.connect(config.imapHost, config.imapPort, config.username, config.password)
        try {
            return block(store)
        } finally {
            try { store.close() } catch (_: Exception) { /* already closed */ }
        }
    }

    /** Opens the connection only to check the settings and password; throws if something is wrong. */
    fun check() = withStore { }

    fun folders(store: Store): List<RemoteFolder> {
        val all = store.defaultFolder.list("*")
        return all.mapNotNull { f ->
            val imap = f as? IMAPFolder ?: return@mapNotNull null
            val attrs = imap.attributes.map { it.lowercase() }
            if ("\\noselect" in attrs) return@mapNotNull null
            RemoteFolder(imap.fullName, roleOf(imap.fullName, attrs))
        }
    }

    private fun roleOf(path: String, attrs: List<String>): Role {
        if (path.equals("INBOX", ignoreCase = true)) return Role.INBOX
        when {
            "\\sent" in attrs -> return Role.SENT
            "\\drafts" in attrs -> return Role.DRAFTS
            "\\trash" in attrs -> return Role.TRASH
            "\\archive" in attrs || "\\all" in attrs -> return Role.ARCHIVE
            "\\junk" in attrs -> return Role.JUNK
        }
        val name = path.substringAfterLast('/').substringAfterLast('.').lowercase()
        return when {
            name in setOf("sent", "sent items", "sent mail", "gesendet", "enviados", "elementos enviados", "envoyés") -> Role.SENT
            name in setOf("drafts", "draft", "entwürfe", "borradores", "brouillons") -> Role.DRAFTS
            name in setOf("trash", "deleted items", "deleted messages", "papierkorb", "papelera", "corbeille", "gelöscht") -> Role.TRASH
            name in setOf("archive", "archives", "archiv", "archivo") -> Role.ARCHIVE
            name in setOf("junk", "spam", "junk e-mail", "bulk mail", "correo no deseado") -> Role.JUNK
            else -> Role.OTHER
        }
    }

    fun <T> inFolder(store: Store, path: String, readOnly: Boolean = false, block: (IMAPFolder) -> T): T {
        val folder = store.getFolder(path) as IMAPFolder
        folder.open(if (readOnly) Folder.READ_ONLY else Folder.READ_WRITE)
        try {
            return block(folder)
        } finally {
            try { folder.close(false) } catch (_: Exception) { /* connection already gone */ }
        }
    }

    /** The newest [limit] messages of the folder, with their envelope and flags already fetched. */
    fun window(folder: IMAPFolder, limit: Int): List<RemoteMessage> {
        val count = folder.messageCount
        if (count == 0) return emptyList()
        val messages = folder.getMessages(maxOf(1, count - limit + 1), count)
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
        }
        folder.fetch(messages, profile)
        return messages.map { m ->
            RemoteMessage(
                uid = folder.getUID(m),
                seen = m.isSet(Flags.Flag.SEEN),
                flagged = m.isSet(Flags.Flag.FLAGGED),
                message = m,
            )
        }
    }

    fun byUid(folder: IMAPFolder, uid: Long): Message? = folder.getMessageByUID(uid)

    /** Reads sender, subject, date and the text and HTML parts of a message (attachments are not downloaded). */
    fun parse(message: Message): ParsedMessage {
        val from = (message.from?.firstOrNull() as? InternetAddress)
        val email = from?.address.orEmpty()
        val name = from?.personal?.takeIf { it.isNotBlank() } ?: email
        var text: String? = null
        var html: String? = null
        var attachment = false
        fun walk(part: Part) {
            val disposition = part.disposition
            val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
                (part.fileName != null && !part.isMimeType("text/*") && !part.isMimeType("multipart/*") &&
                    !(part.isMimeType("image/*") && (part as? javax.mail.internet.MimePart)?.contentID != null))
            when {
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as Multipart
                    for (i in 0 until multipart.count) walk(multipart.getBodyPart(i))
                }
                isAttachment -> attachment = true
                part.isMimeType("text/plain") && text == null -> text = readText(part)
                part.isMimeType("text/html") && html == null -> html = readText(part)
            }
        }
        try { walk(message) } catch (_: Exception) { /* a broken part: keep what was read */ }
        val plain = text?.let(::decodeEntities) ?: html?.let { Jsoup.parse(it).text() }.orEmpty()
        val time = (message.receivedDate ?: message.sentDate ?: Date(0)).time
        return ParsedMessage(
            subject = try { message.subject.orEmpty() } catch (_: Exception) { "" },
            fromName = name, fromEmail = email,
            to = message.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { (it as? InternetAddress)?.address ?: it.toString() }.orEmpty(),
            body = plain, timeMillis = time, hasAttachment = attachment, labels = emptySet(), html = html.orEmpty(),
        )
    }

    private fun readText(part: Part): String = try {
        part.content as? String ?: part.inputStream.readBytes().toString(Charsets.UTF_8)
    } catch (_: Exception) {
        // An unknown charset: read the bytes as UTF-8 rather than losing the text.
        part.inputStream.readBytes().toString(Charsets.UTF_8)
    }

    fun setSeen(folder: IMAPFolder, uid: Long, seen: Boolean) {
        byUid(folder, uid)?.setFlag(Flags.Flag.SEEN, seen)
    }

    fun setFlagged(folder: IMAPFolder, uid: Long, flagged: Boolean) {
        byUid(folder, uid)?.setFlag(Flags.Flag.FLAGGED, flagged)
    }

    fun move(store: Store, from: IMAPFolder, uid: Long, targetPath: String) {
        val message = byUid(from, uid) ?: return
        val target = store.getFolder(targetPath) as IMAPFolder
        from.moveMessages(arrayOf(message), target)
    }

    /** Deletes the message for good (used when the account has no Trash folder). */
    fun expunge(folder: IMAPFolder, uid: Long) {
        byUid(folder, uid)?.setFlag(Flags.Flag.DELETED, true)
        folder.expunge()
    }

    /** Deletes every message of the folder for good. */
    fun expungeAll(folder: IMAPFolder) {
        val all = folder.messages
        if (all.isEmpty()) return
        folder.setFlags(all, Flags(Flags.Flag.DELETED), true)
        folder.expunge()
    }

    fun raw(folder: IMAPFolder, uid: Long): ByteArray? {
        val message = byUid(folder, uid) ?: return null
        return ByteArrayOutputStream().also { message.writeTo(it) }.toByteArray()
    }

    /** The headers a reply needs, read from the server. */
    fun headers(folder: IMAPFolder, uid: Long, names: List<String>): Map<String, String> {
        val message = byUid(folder, uid) ?: return emptyMap()
        return names.mapNotNull { n -> message.getHeader(n)?.joinToString(", ")?.let { n to MimeUtility.unfold(it) } }.toMap()
    }

    /** The attached files (their id is the part's position, like "2.1"). */
    fun attachments(folder: IMAPFolder, uid: Long): List<AttachmentInfo> {
        val message = byUid(folder, uid) ?: return emptyList()
        val found = mutableListOf<AttachmentInfo>()
        fun walk(part: Part, path: String) {
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as Multipart
                for (i in 0 until multipart.count) walk(multipart.getBodyPart(i), if (path.isEmpty()) "$i" else "$path.$i")
            } else {
                val name = part.fileName?.let { MimeUtility.decodeText(it) }
                val inlineImage = part.isMimeType("image/*") && (part as? javax.mail.internet.MimePart)?.contentID != null
                if (name != null && !inlineImage && !part.isMimeType("text/html")) {
                    found += AttachmentInfo(path, name, part.contentType.substringBefore(';').trim(), maxOf(part.size, 0))
                }
            }
        }
        try { walk(message, "") } catch (_: Exception) { /* partial list */ }
        return found
    }

    /** The pictures a message embeds (parts with a Content-ID), downloaded in full. */
    fun inlineImages(folder: IMAPFolder, uid: Long): List<com.cemail.app.data.mail.FetchedImage> {
        val message = byUid(folder, uid) ?: return emptyList()
        val found = mutableListOf<com.cemail.app.data.mail.FetchedImage>()
        fun walk(part: Part) {
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as Multipart
                for (i in 0 until multipart.count) walk(multipart.getBodyPart(i))
            } else if (part.isMimeType("image/*")) {
                val cid = com.cemail.app.data.mail.InlineCache.cleanCid((part as? javax.mail.internet.MimePart)?.contentID)
                if (cid != null) {
                    found += com.cemail.app.data.mail.FetchedImage(cid, part.contentType.substringBefore(';').trim().lowercase(), part.inputStream.readBytes())
                }
            }
        }
        try { walk(message) } catch (_: Exception) { /* keep what was read */ }
        return found
    }

    fun attachmentBytes(folder: IMAPFolder, uid: Long, partPath: String): ByteArray? {
        var part: Part = byUid(folder, uid) ?: return null
        for (index in partPath.split('.').filter { it.isNotEmpty() }.map { it.toInt() }) {
            part = (part.content as Multipart).getBodyPart(index)
        }
        return part.inputStream.readBytes()
    }

    /** Sends [raw] (a complete message without From) over SMTP and returns it as it went out, to be kept in Sent. */
    fun send(raw: ByteArray, displayName: String? = null): ByteArray {
        val message = MimeMessage(session, ByteArrayInputStream(raw))
        message.setFrom(InternetAddress(config.email, displayName?.takeIf { it.isNotBlank() } ?: config.displayName, "UTF-8"))
        message.sentDate = Date()
        message.saveChanges()
        val transport = session.getTransport(smtpProtocol)
        transport.connect(config.smtpHost, config.smtpPort, config.username, config.password)
        try {
            transport.sendMessage(message, message.allRecipients)
        } finally {
            try { transport.close() } catch (_: Exception) { /* nothing left to do */ }
        }
        return ByteArrayOutputStream().also { message.writeTo(it) }.toByteArray()
    }

    /** Keeps a copy of a sent message in the Sent folder (SMTP servers do not do it, unlike Gmail). */
    fun appendTo(store: Store, path: String, raw: ByteArray, seen: Boolean) {
        val folder = store.getFolder(path) as IMAPFolder
        val message = MimeMessage(session, ByteArrayInputStream(raw))
        if (seen) message.setFlag(Flags.Flag.SEEN, true)
        folder.appendMessages(arrayOf(message))
    }
}

private val ENTITY = Regex("""&(#\d{1,7}|#x[0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});""")

/**
 * Some webmails (A1/aon among them) write HTML entities such as `&nbsp;` or `&auml;` into the text/plain part too:
 * they are turned back into the characters, so previews read "Bestätigung" and not "Best&auml;tigung".
 */
internal fun decodeEntities(text: String): String =
    if (ENTITY.containsMatchIn(text)) org.jsoup.parser.Parser.unescapeEntities(text, false).replace('\u00A0', ' ') else text
