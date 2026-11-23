package com.vibecraft.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOrderTest {
    private fun c(id: String, limit: Int = 0, used: Int = 0, key: Boolean = true) = Candidate(id, id.uppercase(), key, limit, used)
    private fun order(s: Selection) = s as Selection.Order
    private fun blocked(s: Selection) = (s as Selection.Blocked).message

    @Test fun noKeysMeansBlocked() {
        assertTrue(blocked(ProviderOrder.choose(listOf(c("a", key = false)), "a", true)).contains("Add an AI profile"))
        assertTrue(blocked(ProviderOrder.choose(emptyList(), null, true)).contains("Add an AI profile"))
    }

    @Test fun theActiveProfileGoesFirstThenTheListOrder() {
        val s = order(ProviderOrder.choose(listOf(c("a"), c("b"), c("c")), "b", true))
        assertEquals(listOf("b", "a", "c"), s.ids)
        assertNull(s.notice)
    }

    @Test fun anUnknownActiveProfileKeepsTheListOrder() {
        assertEquals(listOf("a", "b"), order(ProviderOrder.choose(listOf(c("a"), c("b")), "zzz", true)).ids)
    }

    @Test fun profilesWithoutAKeyAreIgnored() {
        assertEquals(listOf("b"), order(ProviderOrder.choose(listOf(c("a", key = false), c("b")), "a", true)).ids)
    }

    @Test fun aProfileAtItsDailyLimitIsSkippedWhenAutoSwitchIsOn() {
        val s = order(ProviderOrder.choose(listOf(c("a", limit = 5, used = 5), c("b")), "a", true))
        assertEquals(listOf("b"), s.ids)
        assertTrue(s.notice!!.contains("Switched to B"))
    }

    @Test fun withAutoSwitchOffAnActiveProfileOverTheLimitBlocks() {
        assertTrue(blocked(ProviderOrder.choose(listOf(c("a", limit = 5, used = 5), c("b")), "a", false)).contains("Auto-switch is off"))
    }

    @Test fun everyProfileAtItsLimitBlocks() {
        val m = blocked(ProviderOrder.choose(listOf(c("a", limit = 1, used = 1), c("b", limit = 2, used = 3)), "a", true))
        assertTrue(m.contains("daily request limit"))
    }

    @Test fun aLimitOfZeroMeansNoLimit() {
        assertEquals(listOf("a"), order(ProviderOrder.choose(listOf(c("a", limit = 0, used = 999)), "a", true)).ids)
    }

    @Test fun justBelowTheLimitIsStillReady() {
        assertEquals(listOf("a"), order(ProviderOrder.choose(listOf(c("a", limit = 5, used = 4)), "a", true)).ids)
    }
}
