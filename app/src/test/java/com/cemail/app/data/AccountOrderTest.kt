package com.cemail.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountOrderTest {
    @Test fun emptyOrderKeepsTheList() {
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").orderedBy(emptyList()) { it })
    }

    @Test fun followsTheOrderAndPutsUnknownAccountsLastInTheirOwnOrder() {
        assertEquals(listOf("c", "a", "b", "d"), listOf("a", "b", "c", "d").orderedBy(listOf("c", "a")) { it })
    }

    @Test fun idsThatNoLongerExistAreIgnored() {
        assertEquals(listOf("b", "a"), listOf("a", "b").orderedBy(listOf("x", "b", "a")) { it })
    }
}
