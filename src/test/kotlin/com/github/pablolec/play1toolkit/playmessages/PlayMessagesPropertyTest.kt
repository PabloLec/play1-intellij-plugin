package com.github.pablolec.play1toolkit.playmessages

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import org.junit.Assert.*
import org.junit.Test

class PlayMessagesPropertyTest {

    // Tests for locale extraction logic (mirrors PlayMessagesFile.locale)
    // These test the static localeFromFileName function since we can't create PSI without the platform

    @Test
    fun `locale is null for bare messages file`() {
        assertNull(PlayMessagesService.localeFromFileName("messages"))
    }

    @Test
    fun `locale is fr for messages_fr`() {
        assertEquals("fr", PlayMessagesService.localeFromFileName("messages.fr"))
    }

    @Test
    fun `locale is en-US for messages_en-US`() {
        assertEquals("en-US", PlayMessagesService.localeFromFileName("messages.en-US"))
    }

    // Tests for countPlaceholders (value text processing logic)
    @Test
    fun `value with no placeholder has 0 placeholders`() {
        assertEquals(0, PlayMessagesService.countPlaceholders("Hello World"))
    }

    @Test
    fun `value with s specifier has 1 placeholder`() {
        assertEquals(1, PlayMessagesService.countPlaceholders("Hello %s!"))
    }

    @Test
    fun `value with d specifier has 1 placeholder`() {
        assertEquals(1, PlayMessagesService.countPlaceholders("You have %d messages"))
    }

    @Test
    fun `value with escaped percent has 0 placeholders`() {
        assertEquals(0, PlayMessagesService.countPlaceholders("100%% complete"))
    }
}
