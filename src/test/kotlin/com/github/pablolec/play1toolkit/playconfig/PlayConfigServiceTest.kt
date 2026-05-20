package com.github.pablolec.play1toolkit.playconfig

import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigKnownKeys
import org.junit.Assert.*
import org.junit.Test

class PlayConfigServiceTest {

    // --- PlayConfigKnownKeys ---

    @Test
    fun `application_mode is a known key`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("application.mode"))
    }

    @Test
    fun `db_url is a known key`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("db.url"))
    }

    @Test
    fun `http_port is a known key`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("http.port"))
    }

    @Test
    fun `jpa_ddl is a known key`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("jpa.ddl"))
    }

    @Test
    fun `hibernate_show_sql matches hibernate prefix`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("hibernate.show_sql"))
    }

    @Test
    fun `mail_smtp_host matches mail_smtp prefix`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("mail.smtp.host"))
    }

    @Test
    fun `kafka_bootstrap_servers matches kafka prefix`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("kafka.bootstrap.servers"))
    }

    @Test
    fun `cron_export_tarifs matches cron prefix`() {
        assertTrue(PlayConfigKnownKeys.isKnownKey("cron.export.tarifs"))
    }

    @Test
    fun `custom_app_key is not a known key`() {
        assertFalse(PlayConfigKnownKeys.isKnownKey("myapp.some.custom.key"))
    }

    // --- extractEnvVarNames ---

    @Test
    fun `extractEnvVarNames finds single placeholder`() {
        val svc = PlayConfigServiceStub()
        val vars = svc.extractEnvVarNames("\${DATABASE_URL}")
        assertEquals(listOf("DATABASE_URL"), vars)
    }

    @Test
    fun `extractEnvVarNames finds multiple placeholders`() {
        val svc = PlayConfigServiceStub()
        val vars = svc.extractEnvVarNames("jdbc:\${DB_HOST}:3306/\${DB_NAME}")
        assertEquals(listOf("DB_HOST", "DB_NAME"), vars)
    }

    @Test
    fun `extractEnvVarNames returns empty for plain value`() {
        val svc = PlayConfigServiceStub()
        val vars = svc.extractEnvVarNames("jdbc:mysql://localhost/db")
        assertTrue(vars.isEmpty())
    }

}

private class PlayConfigServiceStub {
    fun extractEnvVarNames(value: String): List<String> {
        val pattern = Regex("""\$\{([^}]+)}""")
        return pattern.findAll(value).map { it.groupValues[1] }.toList()
    }
}
