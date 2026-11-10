package com.cemail.app

import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.LabelDiff
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.sendersOf
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsAndSendersTest {
    private fun mail(id: Long, name: String, email: String, folder: Folder = Folder.INBOX, labels: Set<String> = emptySet()) =
        Mail(id = id, senderName = name, senderEmail = email, subject = "s", body = "b", timeMillis = id, folder = folder, labelIds = labels)

    @Test
    fun userLabelsPutOnAndTakenOffAreSentToGmail() {
        val before = mail(1, "A", "a@x.com", labels = setOf("INBOX", "Label_1"))
        val after = before.copy(labelIds = setOf("INBOX", "Label_2"))
        val (add, remove) = LabelDiff.between(before, after)
        assertEquals(listOf("Label_2"), add)
        assertEquals(listOf("Label_1"), remove)
    }

    @Test
    fun systemLabelsAreNeverTouchedThroughTheLabelList() {
        val before = mail(1, "A", "a@x.com", labels = setOf("INBOX", "UNREAD"))
        val after = before.copy(labelIds = setOf("CATEGORY_UPDATES"))
        val (add, remove) = LabelDiff.between(before, after)
        assertEquals(emptyList<String>(), add)
        assertEquals(emptyList<String>(), remove)
    }

    @Test
    fun sendersAreOnePerAddressMostFrequentFirstAndNamed() {
        val mails = listOf(
            mail(1, "bob@y.com", "bob@y.com"),
            mail(2, "Ana", "ana@x.com"),
            mail(3, "Bob Smith", "BOB@y.com"),
            mail(4, "Bob Smith", "bob@y.com"),
            mail(5, "Me", "me@x.com", folder = Folder.SENT),
        )
        val senders = sendersOf(mails)
        assertEquals(listOf("bob@y.com", "ana@x.com"), senders.map { it.email.lowercase() })
        assertEquals("Bob Smith", senders.first().name)
    }
}
