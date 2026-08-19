package com.caloly.app.domain.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialErrorMessageTest {
    @Test
    fun `schema errors become a friendly message without technical details`() {
        val message = IllegalStateException(
            "PGRST202 Could not find public.search_caloly_profiles in the schema cache Headers: secret",
        ).toSocialUserMessage()

        assertTrue(message.contains("Arkadaş"))
        assertFalse(message.contains("PGRST"))
        assertFalse(message.contains("schema", ignoreCase = true))
        assertFalse(message.contains("Headers", ignoreCase = true))
    }

    @Test
    fun `unknown errors never expose their raw message`() {
        val message = IllegalStateException("internal-service-detail").toSocialUserMessage()

        assertFalse(message.contains("internal-service-detail"))
        assertTrue(message.contains("Tekrar"))
    }
}
