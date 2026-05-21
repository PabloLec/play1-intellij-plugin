package com.github.pablolec.play1toolkit.playcache

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateValueResolver
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PlayCacheIntelligenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        addJavaStub(
            "play/cache/Cache.java",
            """
            package play.cache;
            import java.util.concurrent.Callable;
            public class Cache {
                public static Object get(String key) { return null; }
                public static <T> T getOrElse(String key, Callable<T> block, String expiration) { return null; }
                public static void set(String key, Object value) {}
                public static void set(String key, Object value, String expiration) {}
                public static void add(String key, Object value) {}
                public static void add(String key, Object value, String expiration) {}
                public static void safeAdd(String key, Object value, String expiration) {}
                public static void replace(String key, Object value) {}
                public static void replace(String key, Object value, String expiration) {}
                public static void safeReplace(String key, Object value, String expiration) {}
                public static void delete(String key) {}
                public static void safeDelete(String key) {}
                public static void clear() {}
                public static long incr(String key) { return 0L; }
                public static long decr(String key) { return 0L; }
            }
            """
        )
        addJavaStub(
            "play/cache/CacheFor.java",
            """
            package play.cache;
            public @interface CacheFor { String value() default ""; }
            """
        )
        addJavaStub(
            "play/Play.java",
            """
            package play;
            public class Play {
                public static final Configuration configuration = new Configuration();
                public static class Configuration {
                    public String getProperty(String key) { return null; }
                }
            }
            """
        )
        addJavaStub(
            "play/mvc/Controller.java",
            """
            package play.mvc;
            public class Controller {
                public static final Scope.RenderArgs renderArgs = new Scope.RenderArgs();
                public static class Scope {
                    public static class RenderArgs {
                        public void put(String key, Object value) {}
                    }
                }
            }
            """
        )
    }

    private fun addJavaStub(path: String, source: String) {
        myFixture.addFileToProject(path, source.trimIndent())
    }

    private fun addProjectFile(path: String, source: String) =
        myFixture.addFileToProject(path, source.trimIndent())

    fun `test A template static cache fragment is detected`() {
        addProjectFile(
            "app/views/Application/cachedFragment.html",
            """
            <div>
            #{cache 'dashboard', for:'10mn'}
                Content here
            #{/cache}
            </div>
            """
        )

        val fragments = PlayCacheService.getInstance(project).getTemplateFragments()
        assertEquals(1, fragments.size)
        val fragment = fragments.single()
        assertEquals(PlayCacheKey.Static("dashboard"), fragment.key)
        assertEquals(PlayCacheTtl.Static("10mn"), fragment.ttl)
    }

    fun `test B template dynamic cache fragment with includes`() {
        addProjectFile(
            "app/views/BackOfficeCtl/index.html",
            """
            <div>
            #{cache "${'$'}{cacheName}", for:"${'$'}{cacheExpiration}"}
                #{include 'BackOfficeCtl/backOffice.html'/}
            #{/cache}
            </div>
            """
        )

        val fragments = PlayCacheService.getInstance(project).getTemplateFragments()
        assertEquals(1, fragments.size)
        val fragment = fragments.single()
        assertTrue(fragment.key is PlayCacheKey.Dynamic)
        assertTrue(fragment.ttl is PlayCacheTtl.Dynamic)
        assertEquals(listOf("BackOfficeCtl/backOffice.html"), fragment.includedTemplatePaths)
    }

    fun `test C template cache without expiration is parsed as Absent`() {
        addProjectFile(
            "app/views/Application/nottl.html",
            """
            #{cache 'dashboard'}
                Content
            #{/cache}
            """
        )

        val fragment = PlayCacheService.getInstance(project).getTemplateFragments().single()
        assertEquals(PlayCacheKey.Static("dashboard"), fragment.key)
        assertEquals(PlayCacheTtl.Absent, fragment.ttl)
    }

    fun `test D CacheFor annotation is indexed as cached action`() {
        addProjectFile(
            "app/controllers/Application.java",
            """
            package controllers;
            import play.cache.CacheFor;
            public class Application {
                @CacheFor("1h")
                public static void index() {}
            }
            """
        )

        val cached = PlayCacheService.getInstance(project).getCachedActions()
        assertEquals(1, cached.size)
        val info = cached.single()
        assertEquals("index", info.actionMethod.name)
        assertEquals(PlayCacheTtl.Static("1h"), info.ttl)
    }

    fun `test E Java read write delete are classified by kind on the same static key`() {
        addProjectFile(
            "app/controllers/Dashboard.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Dashboard {
                public static void show() {
                    Cache.set("dashboard", "value", "10mn");
                    Cache.get("dashboard");
                    Cache.delete("dashboard");
                }
            }
            """
        )

        val usages = PlayCacheService.getInstance(project).getUsagesByStaticKey("dashboard")
        val kinds = usages.map { it.kind }.toSet()
        assertEquals(
            setOf(
                PlayCacheUsageKind.JAVA_WRITE,
                PlayCacheUsageKind.JAVA_READ,
                PlayCacheUsageKind.JAVA_INVALIDATION
            ),
            kinds
        )
    }

    fun `test F string concatenation key is parsed as Pattern`() {
        addProjectFile(
            "app/controllers/Users.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Users {
                public static void show(long id) {
                    Cache.set("user:" + id, "u", "1h");
                }
            }
            """
        )

        val usages = PlayCacheService.getInstance(project).getAllUsages()
            .filter { it.kind == PlayCacheUsageKind.JAVA_WRITE }
        assertEquals(1, usages.size)
        val key = usages.single().key
        assertTrue("expected Pattern, got $key", key is PlayCacheKey.Pattern)
        assertTrue((key as PlayCacheKey.Pattern).value.startsWith("user:"))
    }

    fun `test G non-literal key is parsed as Dynamic`() {
        addProjectFile(
            "app/controllers/Lookup.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Lookup {
                public static void show(String cacheName) {
                    Cache.get(cacheName);
                }
            }
            """
        )

        val usages = PlayCacheService.getInstance(project).getDynamicUsages()
        assertEquals(1, usages.size)
        assertTrue(usages.single().key is PlayCacheKey.Dynamic)
    }

    fun `test H Cache set without ttl is detected as Absent`() {
        addProjectFile(
            "app/controllers/NoTtl.java",
            """
            package controllers;
            import play.cache.Cache;
            public class NoTtl {
                public static void run() {
                    Cache.set("dashboard", "value");
                }
            }
            """
        )

        val usage = PlayCacheService.getInstance(project).getAllUsages()
            .single { it.kind == PlayCacheUsageKind.JAVA_WRITE }
        assertEquals(PlayCacheTtl.Absent, usage.ttl)
    }

    fun `test I Cache clear is exposed via getGlobalClears`() {
        addProjectFile(
            "app/controllers/Admin.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Admin {
                public static void flushAll() {
                    Cache.clear();
                }
            }
            """
        )

        val clears = PlayCacheService.getInstance(project).getGlobalClears()
        assertEquals(1, clears.size)
        assertEquals(PlayCacheUsageKind.JAVA_CLEAR, clears.single().kind)
    }

    fun `test J getKnownStaticKeys aggregates static keys from all files`() {
        addProjectFile(
            "app/controllers/A.java",
            """
            package controllers;
            import play.cache.Cache;
            public class A { public static void run() { Cache.set("dashboard", "v", "1h"); } }
            """
        )
        addProjectFile(
            "app/controllers/B.java",
            """
            package controllers;
            import play.cache.Cache;
            public class B { public static void run() { Cache.get("user.profile"); } }
            """
        )

        val keys = PlayCacheService.getInstance(project).getKnownStaticKeys()
        assertTrue(keys.contains("dashboard"))
        assertTrue(keys.contains("user.profile"))
    }

    fun `test K getUsagesByStaticKey aggregates across multiple files`() {
        addProjectFile(
            "app/controllers/Writer.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Writer { public static void run() { Cache.set("dashboard", "v", "10mn"); } }
            """
        )
        addProjectFile(
            "app/controllers/Reader.java",
            """
            package controllers;
            import play.cache.Cache;
            public class Reader { public static void run() { Cache.get("dashboard"); } }
            """
        )

        val usages = PlayCacheService.getInstance(project).getUsagesByStaticKey("dashboard")
        assertEquals(2, usages.size)
        assertTrue(usages.any { it.kind == PlayCacheUsageKind.JAVA_WRITE })
        assertTrue(usages.any { it.kind == PlayCacheUsageKind.JAVA_READ })
    }

    fun `test L empty TTL on CacheFor is preserved as Static empty`() {
        addProjectFile(
            "app/controllers/EmptyTtl.java",
            """
            package controllers;
            import play.cache.CacheFor;
            public class EmptyTtl {
                @CacheFor("")
                public static void index() {}
            }
            """
        )

        val info = PlayCacheService.getInstance(project).getCachedActions().single()
        assertEquals(PlayCacheTtl.Static(""), info.ttl)
    }

    fun `test M template interpolation with literal prefix is parsed as Pattern`() {
        addProjectFile(
            "app/views/Application/pattern.html",
            """
            #{cache "user:${'$'}{user.id}", for:"10mn"}
                Content
            #{/cache}
            """
        )

        val fragment = PlayCacheService.getInstance(project).getTemplateFragments()
            .first { it.templateFile.name == "pattern.html" }
        assertEquals(PlayCacheKey.Pattern("user:\${...}"), fragment.key)
    }

    fun `test N config backed TTL and value type are exposed on Java writes`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            public class User {}
            """
        )
        addProjectFile(
            "app/controllers/CacheWriter.java",
            """
            package controllers;
            import models.User;
            import play.Play;
            import play.cache.Cache;
            public class CacheWriter {
                public static void run(User user) {
                    Cache.set("dashboard", user, Play.configuration.getProperty("cache.dashboard.ttl"));
                }
            }
            """
        )

        val usage = PlayCacheService.getInstance(project).getUsagesByStaticKey("dashboard").single()
        assertEquals("cache.dashboard.ttl", usage.ttlConfigurationKey)
        assertEquals("User", usage.valueType)
    }

    fun `test O controller renderArgs resolve displayed cache key ttl and guard`() {
        addProjectFile(
            "conf/application.conf",
            """
            cache.groovytemplate.enable=off
            cache.depenses.ttl=15mn
            cache.groovytemplate.delay=2000h
            """
        )
        addProjectFile(
            "app/models/core/GMUtils.java",
            """
            package models.core;
            import play.Play;
            public class GMUtils {
                public static String getCacheGroovyTemplateDelay() {
                    return Play.configuration.getProperty("cache.groovytemplate.delay");
                }
                public static boolean useCachedTemplate() {
                    return "on".equals(Play.configuration.getProperty("cache.groovytemplate.enable"));
                }
                public static String getHostName() { return "host"; }
                public static Integer getHttpPort() { return 9000; }
            }
            """
        )
        addProjectFile(
            "app/controllers/GmvetNotSecuredController.java",
            """
            package controllers;
            import models.core.GMUtils;
            import play.mvc.Controller;
            public class GmvetNotSecuredController extends Controller {
                private static final String KEY_CACHE_EXPIRATION = "cacheExpiration";
                private static final String KEY_CACHE_NAME = "cacheName";
                private static final String VAR_ISCACHED = "isCached";
                protected static void setDefaultParamsGroovyTemplate(String tplName) {
                    renderArgs.put(VAR_ISCACHED, GMUtils.useCachedTemplate());
                    renderArgs.put(KEY_CACHE_EXPIRATION, GMUtils.getCacheGroovyTemplateDelay());
                    StringBuilder tplNameSpace = new StringBuilder(GMUtils.getHostName());
                    tplNameSpace.append(".").append(GMUtils.getHttpPort()).append(".").append(tplName);
                    renderArgs.put(KEY_CACHE_NAME, tplNameSpace.toString());
                }
            }
            """
        )
        addProjectFile(
            "app/controllers/StaticCtl.java",
            """
            package controllers;
            public class StaticCtl extends GmvetNotSecuredController {
                private static void screen(String name) {
                    setDefaultParamsGroovyTemplate(name);
                }
                public static void depensesScreen() {
                    screen("depensesScreen");
                }
            }
            """
        )
        addProjectFile(
            "app/views/StaticCtl/depensesScreen.html",
            """
            #{if isCached == true}
            #{cache "${'$'}{cacheName}", for:"${'$'}{cacheExpiration}"}
                Content
            #{/cache}
            #{/if}
            """
        )

        val fragment = PlayCacheService.getInstance(project).getTemplateFragments().single()
        val keyInfo = PlayCacheTemplateValueResolver.resolveKey(project, fragment)
        val ttlInfo = PlayCacheTemplateValueResolver.resolveTtl(project, fragment)
        val guardInfo = PlayCacheTemplateValueResolver.resolveGuard(project, fragment.templateFile)

        assertEquals("\${host}.\${httpPort}.depensesScreen", keyInfo.resolvedValue)
        assertEquals("cache.groovytemplate.delay", ttlInfo.configurationKey)
        assertEquals("2000h", ttlInfo.configurationValue)
        assertEquals(false, guardInfo?.booleanValue)
    }

    fun `test P template cache variables resolve to renderArgs put declarations through helper methods`() {
        addProjectFile(
            "app/models/core/GMUtils.java",
            """
            package models.core;
            public class GMUtils {
                public static String getCacheGroovyTemplateDelay() {
                    return "2000h";
                }
                public static String getHostName() { return "host"; }
                public static Integer getHttpPort() { return 9000; }
            }
            """
        )
        addProjectFile(
            "app/controllers/GmvetNotSecuredController.java",
            """
            package controllers;
            import models.core.GMUtils;
            import play.mvc.Controller;
            public class GmvetNotSecuredController extends Controller {
                private static final String KEY_CACHE_EXPIRATION = "cacheExpiration";
                private static final String KEY_CACHE_NAME = "cacheName";
                protected static void setDefaultParamsGroovyTemplate(String tplName) {
                    renderArgs.put(KEY_CACHE_EXPIRATION, GMUtils.getCacheGroovyTemplateDelay());
                    StringBuilder tplNameSpace = new StringBuilder(GMUtils.getHostName());
                    tplNameSpace.append(".").append(GMUtils.getHttpPort()).append(".").append(tplName);
                    renderArgs.put(KEY_CACHE_NAME, tplNameSpace.toString());
                }
            }
            """
        )
        addProjectFile(
            "app/controllers/StaticCtl.java",
            """
            package controllers;
            public class StaticCtl extends GmvetNotSecuredController {
                private static void screen(String name) {
                    setDefaultParamsGroovyTemplate(name);
                }
                public static void depensesScreen() {
                    screen("depensesScreen");
                }
            }
            """
        )
        val file = addProjectFile(
            "app/views/StaticCtl/depensesScreen.html",
            """
            #{cache "${'$'}{cacheName}", for:"${'$'}{cacheExpiration}"}
                Content
            #{/cache}
            """
        )

        val resolver = PlayTemplateVariableResolver.getInstance(project)
        val cacheNameInfo = resolver.resolveVariableInfo(file, "cacheName")
        val cacheExpirationInfo = resolver.resolveVariableInfo(file, "cacheExpiration")

        val actionMethod = RoutesControllerResolver.resolveMethod(project, "StaticCtl", "depensesScreen")
        assertNotNull(actionMethod)
        val containingClass = actionMethod!!.containingClass
        assertNotNull(containingClass)
        assertNotNull(cacheNameInfo)
        assertNotNull(cacheExpirationInfo)
        assertTrue(cacheNameInfo!!.declaration is com.intellij.psi.PsiMethodCallExpression)
        assertTrue(cacheExpirationInfo!!.declaration is com.intellij.psi.PsiMethodCallExpression)
        assertTrue(cacheNameInfo.declaration.text.contains("renderArgs.put(KEY_CACHE_NAME"))
        assertTrue(cacheExpirationInfo.declaration.text.contains("renderArgs.put(KEY_CACHE_EXPIRATION"))
    }

    fun `test Q ctrl click reference resolves cacheName inside cache tag`() {
        addProjectFile(
            "app/models/core/GMUtils.java",
            """
            package models.core;
            public class GMUtils {
                public static String getCacheGroovyTemplateDelay() {
                    return "2000h";
                }
                public static String getHostName() { return "host"; }
                public static Integer getHttpPort() { return 9000; }
            }
            """
        )
        addProjectFile(
            "app/controllers/GmvetNotSecuredController.java",
            """
            package controllers;
            import models.core.GMUtils;
            import play.mvc.Controller;
            public class GmvetNotSecuredController extends Controller {
                private static final String KEY_CACHE_EXPIRATION = "cacheExpiration";
                private static final String KEY_CACHE_NAME = "cacheName";
                protected static void setDefaultParamsGroovyTemplate(String tplName) {
                    renderArgs.put(KEY_CACHE_EXPIRATION, GMUtils.getCacheGroovyTemplateDelay());
                    StringBuilder tplNameSpace = new StringBuilder(GMUtils.getHostName());
                    tplNameSpace.append(".").append(GMUtils.getHttpPort()).append(".").append(tplName);
                    renderArgs.put(KEY_CACHE_NAME, tplNameSpace.toString());
                }
            }
            """
        )
        addProjectFile(
            "app/controllers/StaticCtl.java",
            """
            package controllers;
            public class StaticCtl extends GmvetNotSecuredController {
                private static void screen(String name) {
                    setDefaultParamsGroovyTemplate(name);
                }
                public static void depensesScreen() {
                    screen("depensesScreen");
                }
            }
            """
        )
        val template = addProjectFile(
            "app/views/StaticCtl/depensesScreen.html",
            """
            #{if isCached == true}
                #{cache "${'$'}{cacheName}", for:"${'$'}{cacheExpiration}" }
                    #{depenses.depensesScreen /}
                #{/cache}
            #{/if}
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(template.virtualFile)

        val offset = myFixture.file.text.indexOf("cacheName") + 2
        val element = myFixture.file.findElementAt(offset)
        val ref = myFixture.file.findReferenceAt(offset)
        val context = generateSequence(element) { it.parent }
            .take(5)
            .joinToString(" -> ") { it.javaClass.simpleName + ":" + it.text.take(30).replace('\n', ' ') }
        assertNotNull("element=$context", ref)
        val target = ref!!.resolve()
        assertNotNull(target)
        assertTrue(target!!.text.contains("renderArgs.put(KEY_CACHE_NAME"))
    }

    fun `test methodKind covers all known methods`() {
        val all = com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor.CACHE_METHODS
        assertEquals(PlayCacheUsageKind.JAVA_READ, all["get"])
        assertEquals(PlayCacheUsageKind.JAVA_WRITE, all["set"])
        assertEquals(PlayCacheUsageKind.JAVA_INVALIDATION, all["delete"])
        assertEquals(PlayCacheUsageKind.JAVA_CLEAR, all["clear"])
        assertEquals(PlayCacheUsageKind.JAVA_MUTATION, all["incr"])
        assertEquals(PlayCacheUsageKind.JAVA_READ_OR_COMPUTE, all["getOrElse"])
        assertEquals(PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT, all["add"])
        assertEquals(PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT, all["replace"])
    }
}
