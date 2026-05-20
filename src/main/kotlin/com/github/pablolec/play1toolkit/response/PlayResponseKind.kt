package com.github.pablolec.play1toolkit.response

enum class PlayResponseKind {
    HTML,
    JSON,
    XML,
    TEXT,
    BINARY,
    REDIRECT,
    STATUS,
    ERROR,
    MIXED,
    UNKNOWN,
}

enum class PlayResponseConfidence {
    HIGH,
    MEDIUM,
    LOW,
}
