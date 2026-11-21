package com.vibecraft.app.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidationTest {
    @Test fun httpsIsAccepted() {
        assertNull(ProfileValidation.checkUrl("https://api.openai.com/v1"))
        assertNull(ProfileValidation.checkUrl("https://openrouter.ai/api/v1"))
    }

    @Test fun plainHttpOnlyForLocalhost() {
        assertNotNull(ProfileValidation.checkUrl("http://example.com/v1"))
        assertNull(ProfileValidation.checkUrl("http://localhost:8080/v1"))
    }

    @Test fun badUrlsAreRejected() {
        assertNotNull(ProfileValidation.checkUrl(""))
        assertNotNull(ProfileValidation.checkUrl("not a url"))
        assertNotNull(ProfileValidation.checkUrl("ftp://example.com"))
        assertNotNull(ProfileValidation.checkUrl("https://user:pass@example.com/v1"))
        assertNotNull(ProfileValidation.checkUrl("https://example.com/v1?key=abc"))
    }
}
