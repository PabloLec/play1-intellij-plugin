package com.github.pablolec.play1toolkit.threading

import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjpa.service.PlayAppModelClassificationService
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.github.pablolec.play1toolkit.project.Play1ModuleResolver
import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

class IntelliJThreadingAccessTest : BasePlatformTestCase() {

    fun `test services tolerate pooled thread calls without caller read action`() {
        createPlayProject()
        val templateFile = myFixture.findFileInTempDir("app/views/Application/index.html")
        val templatePsi = ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiFile?> {
            PsiManager.getInstance(project).findFile(templateFile)
        }
        assertNotNull(templatePsi)

        val future = AppExecutorUtil.getAppExecutorService().submit<Throwable?> {
            runCatching {
                assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)

                PlayConfigService.getInstance(project).availableProfiles()
                PlayMessagesService.getInstance(project).allEntries()

                val jobService = PlayJobService.getInstance(project)
                val jobs = jobService.getAllJobs()
                jobs.firstOrNull()?.let { jobService.findInvocations(it) }

                PlayJpaModelService.getInstance(project).getAllModels()
                PlayAppModelClassificationService.getInstance(project).getAllEntries()

                val cacheService = PlayCacheService.getInstance(project)
                cacheService.getAllUsages()
                cacheService.getCachedActions()
                cacheService.getTemplateFragments()

                val templateService = PlayTemplateService.getInstance(project)
                templateService.getAllTemplates()
                templateService.getAllCustomTags()

                PlayTemplateVariableResolver.getInstance(project).resolveVariables(templatePsi!!)
                templateService.findLikelyRenderingMethods(templateFile)

                RoutesControllerResolver.resolveMethod(project, "Application", "index")
                Play1ViewUtils.findRoutesForAction(project, "Application", "index")
                Play1ModuleResolver.findModule(project, project.basePath)
            }.exceptionOrNull()
        }

        val error = future.get(15, TimeUnit.SECONDS)
        if (error != null) {
            throw AssertionError("Service access from a pooled thread should be internally read-action safe", error)
        }
    }

    private fun createPlayProject() {
        addFile(
            "conf/application.conf",
            """
            application.name=sample
            %dev.application.mode=dev
            """
        )
        addFile("conf/routes", "GET / Application.index")
        addFile("conf/messages", "home.title=Home")
        addFile(
            "app/views/Application/index.html",
            """
            #{cache 'home', for:'10mn'}
              ${'$'}{title}
            #{/cache}
            """
        )
        addFile("app/views/tags/panel.html", "<div>${'$'}{_body}</div>")
        addFile(
            "play/mvc/Controller.java",
            """
            package play.mvc;
            public class Controller {
                public static final Scope.RenderArgs renderArgs = new Scope.RenderArgs();
                public static void render(Object... args) {}
                public static class Scope {
                    public static class RenderArgs {
                        public void put(String key, Object value) {}
                    }
                }
            }
            """
        )
        addFile(
            "play/jobs/Job.java",
            """
            package play.jobs;
            public class Job {
                public Job now() { return this; }
            }
            """
        )
        addFile(
            "play/jobs/OnApplicationStart.java",
            """
            package play.jobs;
            public @interface OnApplicationStart {}
            """
        )
        addFile(
            "play/cache/Cache.java",
            """
            package play.cache;
            public class Cache {
                public static void set(String key, Object value, String expiration) {}
            }
            """
        )
        addFile(
            "play/cache/CacheFor.java",
            """
            package play.cache;
            public @interface CacheFor { String value() default ""; }
            """
        )
        addFile(
            "play/db/jpa/Model.java",
            """
            package play.db.jpa;
            public class Model {
                public Long id;
            }
            """
        )
        addFile(
            "app/controllers/Application.java",
            """
            package controllers;
            import play.cache.Cache;
            import play.cache.CacheFor;
            import play.mvc.Controller;
            public class Application extends Controller {
                @CacheFor("1h")
                public static void index() {
                    String title = "Dashboard";
                    renderArgs.put("title", title);
                    Cache.set("home", title, "10mn");
                    render(title);
                }
            }
            """
        )
        addFile(
            "app/jobs/StartupJob.java",
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
        addFile(
            "app/controllers/JobsController.java",
            """
            package controllers;
            import jobs.StartupJob;
            public class JobsController {
                public static void start() {
                    new StartupJob().now();
                }
            }
            """
        )
        addFile(
            "app/models/Customer.java",
            """
            package models;
            import play.db.jpa.Model;
            public class Customer extends Model {
                public String name;
            }
            """
        )
    }

    private fun addFile(path: String, source: String) {
        myFixture.addFileToProject(path, source.trimIndent())
    }
}
