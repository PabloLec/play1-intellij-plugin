package com.github.pablolec.play1toolkit.playjobs

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobCategory
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobConfidence
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTriggerKind
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PlayJobsSupportTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        addJavaStub(
            "play/jobs/Job.java",
            """
            package play.jobs;
            public class Job {
                public Job now() { return this; }
                public Job in(String duration) { return this; }
                public Job at(String cron) { return this; }
                public Job afterRequest() { return this; }
            }
            """
        )
        addJavaStub(
            "play/jobs/OnApplicationStart.java",
            """
            package play.jobs;
            public @interface OnApplicationStart { boolean async() default false; }
            """
        )
        addJavaStub(
            "play/jobs/OnApplicationStop.java",
            """
            package play.jobs;
            public @interface OnApplicationStop {}
            """
        )
        addJavaStub(
            "play/jobs/Every.java",
            """
            package play.jobs;
            public @interface Every { String value(); }
            """
        )
        addJavaStub(
            "play/jobs/On.java",
            """
            package play.jobs;
            public @interface On { String value(); }
            """
        )
    }

    private fun addJavaStub(path: String, source: String) {
        myFixture.addFileToProject(path, source.trimIndent())
    }

    private fun addProjectFile(path: String, source: String) =
        myFixture.addFileToProject(path, source.trimIndent())

    fun `test A startup job is detected with high confidence`() {
        addProjectFile(
            "app/jobs/BootstrapJob.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.OnApplicationStart;

            @OnApplicationStart
            public class BootstrapJob extends Job {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("BootstrapJob")
        assertNotNull(info)
        assertEquals(PlayJobCategory.STARTUP, info!!.category)
        assertEquals(PlayJobConfidence.HIGH, info.confidence)
        assertEquals(1, info.triggers.size)
        assertEquals(PlayJobTriggerKind.ON_APPLICATION_START, info.triggers.first().kind)
        assertEquals("doJob", info.executionMethods.single().name)
    }

    fun `test B scheduled every job preserves trigger value`() {
        addProjectFile(
            "app/jobs/ImportJob.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.Every;

            @Every("1h")
            public class ImportJob extends Job {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("ImportJob")
        assertNotNull(info)
        assertEquals(PlayJobCategory.SCHEDULED_EVERY, info!!.category)
        val trigger = info.triggers.single()
        assertEquals(PlayJobTriggerKind.EVERY, trigger.kind)
        assertEquals("1h", trigger.rawValue)
    }

    fun `test C cron job recognized through On annotation`() {
        addProjectFile(
            "app/jobs/BillingJob.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.On;

            @On("0 0 3 * * ?")
            public class BillingJob extends Job {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("BillingJob")
        assertNotNull(info)
        assertEquals(PlayJobCategory.SCHEDULED_CRON, info!!.category)
        assertEquals(PlayJobTriggerKind.ON, info.triggers.single().kind)
        assertEquals("0 0 3 * * ?", info.triggers.single().rawValue)
    }

    fun `test D shutdown job is detected`() {
        addProjectFile(
            "app/jobs/CleanupJob.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.OnApplicationStop;

            @OnApplicationStop
            public class CleanupJob extends Job {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("CleanupJob")
        assertNotNull(info)
        assertEquals(PlayJobCategory.SHUTDOWN, info!!.category)
    }

    fun `test E manual invocation is discovered with kind NOW`() {
        addProjectFile(
            "app/jobs/MailerJob.java",
            """
            package jobs;
            import play.jobs.Job;
            public class MailerJob extends Job {
                public void doJob() {}
            }
            """
        )
        addProjectFile(
            "app/controllers/Application.java",
            """
            package controllers;
            import jobs.MailerJob;
            public class Application {
                public static void index() {
                    new MailerJob().now();
                }
            }
            """
        )

        val service = PlayJobService.getInstance(project)
        val job = service.findJobByName("MailerJob")
        assertNotNull(job)
        val invocations = service.findInvocations(job!!)
        assertEquals(1, invocations.size)
        assertEquals(PlayJobInvocationKind.NOW, invocations.single().kind)
        assertEquals(PlayJobCategory.MANUAL_ASYNC, job.category)
    }

    fun `test F class annotated as job without extending Job is medium confidence`() {
        addProjectFile(
            "app/jobs/NotAJob.java",
            """
            package jobs;
            import play.jobs.Every;
            @Every("1h")
            public class NotAJob {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("NotAJob")
        assertNotNull(info)
        assertFalse(info!!.extendsPlayJob)
        assertEquals(PlayJobConfidence.MEDIUM, info.confidence)
        assertEquals(PlayJobCategory.SCHEDULED_EVERY, info.category)
    }

    fun `test G suspicious Every value is detected via util`() {
        assertFalse(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid(""))
        assertFalse(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid("forever"))
        assertTrue(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid("1h"))
        assertTrue(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid("10mn"))
        assertTrue(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid("15s"))
        assertTrue(com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils.parseEveryValueIsValid("1d"))
    }

    fun `test H positional fallback creates low confidence Unknown job`() {
        addProjectFile(
            "app/jobs/LegacyBatchJob.java",
            """
            package jobs;
            public class LegacyBatchJob {
                public void run() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("LegacyBatchJob")
        assertNotNull(info)
        assertEquals(PlayJobConfidence.LOW, info!!.confidence)
        assertEquals(PlayJobCategory.UNKNOWN, info.category)
    }

    fun `test I delayed invocation detected as IN kind`() {
        addProjectFile(
            "app/jobs/ReminderJob.java",
            """
            package jobs;
            import play.jobs.Job;
            public class ReminderJob extends Job {
                public void doJob() {}
            }
            """
        )
        addProjectFile(
            "app/controllers/Reminders.java",
            """
            package controllers;
            import jobs.ReminderJob;
            public class Reminders {
                public static void schedule() {
                    new ReminderJob().in("5mn");
                }
            }
            """
        )

        val service = PlayJobService.getInstance(project)
        val job = service.findJobByName("ReminderJob")
        assertNotNull(job)
        val invocations = service.findInvocations(job!!)
        assertEquals(PlayJobInvocationKind.IN, invocations.single().kind)
    }

    fun `test detection picks up async startup flag`() {
        addProjectFile(
            "app/jobs/AsyncBootstrap.java",
            """
            package jobs;
            import play.jobs.Job;
            import play.jobs.OnApplicationStart;
            @OnApplicationStart(async = true)
            public class AsyncBootstrap extends Job {
                public void doJob() {}
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("AsyncBootstrap")
        assertNotNull(info)
        assertTrue(info!!.triggers.single().async)
    }

    fun `test doJobWithResult is recognized as returning result`() {
        addProjectFile(
            "app/jobs/QueryJob.java",
            """
            package jobs;
            import play.jobs.Job;
            public class QueryJob extends Job {
                public String doJobWithResult() { return "ok"; }
            }
            """
        )

        val info = PlayJobService.getInstance(project).findJobByName("QueryJob")
        assertNotNull(info)
        val method = info!!.executionMethods.single()
        assertEquals("doJobWithResult", method.name)
        assertTrue(method.returnsResult)
    }
}
