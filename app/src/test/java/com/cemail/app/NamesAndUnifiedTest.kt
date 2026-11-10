package com.cemail.app

import com.cemail.app.data.mail.DemoMailRepository
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.UnifiedMailRepository
import com.cemail.app.data.mail.withKnownNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesAndUnifiedTest {
    private fun mail(id: Long, name: String, email: String, folder: Folder = Folder.INBOX) =
        Mail(id, name, email, "s", "b", id, folder = folder)

    @Test fun bareAddressesGetTheNameSeenInOtherMessages() {
        val out = withKnownNames(
            listOf(
                mail(1, "ana@x.com", "ana@x.com"),
                mail(2, "Ana López", "Ana@x.com"),
                mail(3, "nobody@x.com", "nobody@x.com"),
                mail(4, "someone@y.com", "ana@x.com", Folder.DRAFTS), // a draft keeps its recipient
            ),
        )
        assertEquals("Ana López", out[0].senderName)
        assertEquals("nobody@x.com", out[2].senderName)
        assertEquals("someone@y.com", out[3].senderName)
    }

    @Test fun unifiedShowsEveryAccountAndRoutesActionsToTheOwner() = runTest {
        val a = DemoMailRepository(listOf(mail(1, "A", "a@x.com")))
        val b = DemoMailRepository(listOf(mail(2, "B", "b@x.com")))
        val unified = UnifiedMailRepository(listOf(a, b), backgroundScope)
        assertEquals(setOf(1L, 2L), unified.mails.first().map { it.id }.toSet())

        unified.update(setOf(2L)) { it.copy(starred = true) }
        assertTrue(b.mails.value.single().starred)
        assertTrue(!a.mails.value.single().starred)
        assertEquals(listOf(2L), unified.snapshot(setOf(2L)).map { it.id })
        assertTrue(unified.refresh(quick = true))
    }
}
