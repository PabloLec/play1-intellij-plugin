package com.github.pablolec.play1toolkit.playconfig.service

object PlayConfigKnownKeys {

    private val EXACT_KEYS = setOf(
        "application.mode", "application.secret", "application.name", "application.log",
        "application.langs", "application.baseUrl", "application.log.path",
        "application.defaultCookieDomain", "application.forceSecureReverseRoutes",
        "application.modules",
        "application.session.maxAge", "application.session.httpOnly", "application.session.secure",
        "application.session.cookie", "application.session.additionalAttributes",
        "http.port", "http.path", "http.address", "http.cacheControl", "http.useETag",
        "https.port",
        "db", "db.url", "db.driver", "db.user", "db.pass",
        "db.pool.timeout", "db.pool.maxSize", "db.pool.minSize",
        "jpa.ddl", "jpa.dialect", "jpa.debugSQL",
        "evolutions.enabled",
        "attachments.path",
        "mail.smtp", "mail.smtp.host", "mail.smtp.user", "mail.smtp.pass", "mail.smtp.channel",
        "mail.smtp.port", "mail.smtp.protocol",
        "memcached", "memcached.host",
        "play.pool", "play.jobs.pool", "play.editor",
        "jpda.port",
        "date.format", "date.format.es", "date.format.fr",
        "upload.threshold",
        "XForwardedSupport", "XForwardedHost", "XForwardedProto",
        "headersFilter.Order"
    )

    private val KNOWN_PREFIXES = listOf(
        "db.pool.",
        "application.session.",
        "mail.smtp.",
        "memcached.",
        "hibernate.",
        "cron.",
        "jobs.",
        "kafka.",
        "module.",
        "flyway.",
        "modules.",
        "memcached.",
    )

    fun isKnownKey(logicalKey: String): Boolean {
        if (logicalKey in EXACT_KEYS) return true
        return KNOWN_PREFIXES.any { logicalKey.startsWith(it) }
    }

    fun allKnownKeys(): Set<String> = EXACT_KEYS
    fun allKnownPrefixes(): List<String> = KNOWN_PREFIXES
}
