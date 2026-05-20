package com.github.pablolec.play1toolkit.playmessages.model

import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty

data class PlayMessageEntry(
    val key: String,
    val locale: String?,      // null = conf/messages (default)
    val value: String,
    val property: PlayMessagesProperty,
    val lineNumber: Int
)
