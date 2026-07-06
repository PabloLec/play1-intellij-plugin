package com.github.pablolec.play1toolkit.services

import com.github.pablolec.play1toolkit.config.Play1ProjectSettings
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjpa.service.PlayAppModelClassificationService
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

class Play1ProjectPathsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            Play1ProjectSettings.getInstance(project).playApplicationPath = ""
        } finally {
            super.tearDown()
        }
    }

    fun testApplicationPathDoesNotTriggerPlayDetection() {
        myFixture.addFileToProject("conf/application.conf", "")
        myFixture.addFileToProject("conf/routes", "")
        myFixture.addFileToProject("app/controllers/Application.java", "class Application {}")
        Play1ProjectSettings.getInstance(project).playApplicationPath = myFixture.findFileInTempDir("conf/application.conf")
            .parent
            .parent
            .url

        val service = Play1ProjectService.getInstance(project)
        val detectionBefore = service.detectionResult

        val path = Play1ProjectPaths.applicationPath(project)

        assertNotNull(path)
        assertSame(
            "applicationPath must be a cache read, not a detector trigger",
            detectionBefore,
            service.detectionResult
        )
    }

    fun testApplicationPathDoesNotFallbackToWorkspaceParent() {
        Play1ProjectSettings.getInstance(project).playApplicationPath = ""
        myFixture.addFileToProject("other/app/models/Outside.java", "package models; class Outside {}")

        val service = Play1ProjectService.getInstance(project)
        service.refreshNow(reason = "test reset", force = true)
        val detectionBefore = service.detectionResult
        val path = Play1ProjectPaths.applicationPath(project)

        assertNull(path)
        assertSame(
            "applicationPath must not run recursive detection while answering a cached path request",
            detectionBefore,
            service.detectionResult
        )
    }

    fun testServicesStayInsideDetectedNestedPlayApplication() {
        createNestedPlayApplication("apps/sample")
        createNonPlayWorkspaceNoise()
        Play1ProjectSettings.getInstance(project).playApplicationPath = myFixture.findFileInTempDir("apps/sample/conf/application.conf")
            .parent
            .parent
            .url

        assertTrue(Play1ProjectPaths.applicationPath(project)!!.replace('\\', '/').endsWith("/apps/sample"))
        assertEquals(listOf("Customer"), PlayJpaModelService.getInstance(project).getAllModels().map { it.className })
        assertEquals(
            listOf("Customer"),
            PlayAppModelClassificationService.getInstance(project).getAllEntries().map { it.className }
        )
        assertEquals(listOf("StartupJob"), PlayJobService.getInstance(project).getAllJobs().map { it.className })
        assertEquals(
            listOf("Application/index.html"),
            PlayTemplateService.getInstance(project).getAllTemplates().map { it.logicalPath }
        )
        assertEquals(1, PlayCacheService.getInstance(project).getAllUsages().size)
        assertFalse(
            Play1ProjectPaths.isUnderApplicationPath(project, "${project.basePath}/other/app/models/Outside.java")
        )
    }

    private fun createNestedPlayApplication(root: String) {
        addFile("$root/conf/application.conf", "application.name=sample")
        addFile("$root/conf/routes", "GET / Application.index")
        addFile(
            "$root/play/db/jpa/Model.java",
            """
            package play.db.jpa;
            public class Model {
                public Long id;
            }
            """
        )
        addFile(
            "$root/play/jobs/Job.java",
            """
            package play.jobs;
            public class Job {
                public Job now() { return this; }
            }
            """
        )
        addFile(
            "$root/play/jobs/OnApplicationStart.java",
            """
            package play.jobs;
            public @interface OnApplicationStart {}
            """
        )
        addFile(
            "$root/play/cache/Cache.java",
            """
            package play.cache;
            public class Cache {
                public static void set(String key, Object value, String expiration) {}
            }
            """
        )
        addFile(
            "$root/app/controllers/Application.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Application {
                public static void index() {
                    Cache.set("inside", "value", "10mn");
                }
            }
            """
        )
        addFile(
            "$root/app/models/Customer.java",
            """
            package models;
            import play.db.jpa.Model;
            public class Customer extends Model {
                public String name;
            }
            """
        )
        addFile(
            "$root/app/jobs/StartupJob.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.OnApplicationStart;
            @OnApplicationStart
            public class StartupJob extends Job {
                public void doJob() {}
            }
            """
        )
        addFile("$root/app/views/Application/index.html", "<h1>Home</h1>")
    }

    private fun createNonPlayWorkspaceNoise() {
        addFile(
            "other/app/controllers/OutsideController.java",
            """
            package controllers;
            import play.cache.Cache;
            public class OutsideController {
                public static void index() {
                    Cache.set("outside", "value", "10mn");
                }
            }
            """
        )
        addFile(
            "other/app/models/Outside.java",
            """
            package models;
            import play.db.jpa.Model;
            public class Outside extends Model {
                public String name;
            }
            """
        )
        addFile(
            "other/app/jobs/OutsideJob.java",
            """
            package jobs;
            import play.jobs.Job;
            public class OutsideJob extends Job {
                public void doJob() {}
            }
            """
        )
        addFile("other/app/views/Application/outside.html", "<h1>Outside</h1>")
    }

    private fun addFile(path: String, source: String) {
        myFixture.addFileToProject(path, source.trimIndent())
    }
}
