package com.caloly.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameRulesTest {
    @Test fun `accepts Turkish and Unicode letters`() {
        assertTrue(isValidUsername("çağrı.ışık"))
        assertTrue(isValidUsername("Özge_35"))
    }

    @Test fun `rejects spaces and punctuation`() {
        assertFalse(isValidUsername("iki kelime"))
        assertFalse(isValidUsername("ece!"))
    }

    @Test fun `normalizes Turkish case for login`() {
        assertEquals("ışık.çiğdem", normalizeUsername(" IŞIK.ÇİĞDEM "))
    }
}
