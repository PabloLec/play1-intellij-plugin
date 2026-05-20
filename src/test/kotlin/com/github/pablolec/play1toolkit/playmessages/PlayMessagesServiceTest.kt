package com.github.pablolec.play1toolkit.playmessages

import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import org.junit.Assert.*
import org.junit.Test

class PlayMessagesServiceTest {

    // --- countPlaceholders ---

    @Test
    fun `countPlaceholders hello s returns 1`() {
        assertEquals(1, PlayMessagesService.countPlaceholders("Hello %s"))
    }

    @Test
    fun `countPlaceholders s and d returns 2`() {
        assertEquals(2, PlayMessagesService.countPlaceholders("Dear %s, you have %d items"))
    }

    @Test
    fun `countPlaceholders escaped percent returns 0`() {
        assertEquals(0, PlayMessagesService.countPlaceholders("100%%"))
    }

    @Test
    fun `countPlaceholders empty string returns 0`() {
        assertEquals(0, PlayMessagesService.countPlaceholders(""))
    }

    @Test
    fun `countPlaceholders no placeholders returns 0`() {
        assertEquals(0, PlayMessagesService.countPlaceholders("Hello World"))
    }

    @Test
    fun `countPlaceholders mixed escaped percent and s`() {
        assertEquals(1, PlayMessagesService.countPlaceholders("100%% done, hello %s"))
    }

    // --- localeFromFileName ---

    @Test
    fun `localeFromFileName messages returns null`() {
        assertNull(PlayMessagesService.localeFromFileName("messages"))
    }

    @Test
    fun `localeFromFileName messages_fr returns fr`() {
        assertEquals("fr", PlayMessagesService.localeFromFileName("messages.fr"))
    }

    @Test
    fun `localeFromFileName messages_en-US returns en-US`() {
        assertEquals("en-US", PlayMessagesService.localeFromFileName("messages.en-US"))
    }

    @Test
    fun `localeFromFileName messages_zh_CN returns zh_CN`() {
        assertEquals("zh_CN", PlayMessagesService.localeFromFileName("messages.zh_CN"))
    }
}
