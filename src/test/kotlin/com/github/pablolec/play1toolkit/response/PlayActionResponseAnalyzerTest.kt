package com.github.pablolec.play1toolkit.response

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PlayActionResponseAnalyzerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "app/play/mvc/Controller.java",
            """
            package play.mvc;
            public class Controller {
                public static Http.Response response = new Http.Response();
                public static void render(Object... args) {}
                public static void renderTemplate(String template, Object... args) {}
                public static void renderJSON(Object value) {}
                public static void renderXml(Object value) {}
                public static void renderText(Object value) {}
                public static void renderBinary(Object value) {}
                public static void redirect(String action, Object... args) {}
                public static void redirectToStatic(String path) {}
                public static void notFound() {}
                public static void forbidden() {}
                public static void unauthorized() {}
                public static void badRequest() {}
                public static void ok() {}
                public static void error() {}
                public static void notModified() {}
                public static void todo() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "app/play/mvc/Http.java",
            """
            package play.mvc;
            public class Http {
                public static class Response {
                    public int status;
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "app/play/mvc/results/RenderJson.java",
            """
            package play.mvc.results;
            public class RenderJson extends RuntimeException {
                public RenderJson(Object value) {}
            }
            """.trimIndent()
        )
    }

    fun `test html action analysis`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    Object user = new Object();
                    render(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.HTML, info.kind)
        assertEquals("HTML template: Users/show.html", info.outcomes.single().details)
    }

    fun `test json action analysis`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import java.util.List;
            public class Users extends Controller {
                public static void get(Long id) {
                    List<String> user = java.util.List.of("a");
                    renderJSON(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
        assertTrue(info.outcomes.single().details?.startsWith("JSON<") == true)
    }

    fun `test binary action analysis`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import java.io.File;
            public class Files extends Controller {
                public static void download() {
                    File file = new File("/tmp/x");
                    renderBinary(file);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.BINARY, info.kind)
    }

    fun `test redirect action analysis`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void save() {
                    redirect("Users.show", 1L);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.REDIRECT, info.kind)
    }

    fun `test html with not found stays html`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    Object user = null;
                    if (user == null) {
                        notFound();
                    }
                    render(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.HTML, info.kind)
        assertTrue(PlayResponsePresentation.tooltip(info).contains("May also return HTTP 404"))
    }

    fun `test mixed action analysis`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    Object user = new Object();
                    if (id != null) {
                        renderJSON(user);
                    }
                    render(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.MIXED, info.kind)
        assertTrue(PlayResponsePresentation.tooltip(info).contains("Mixed"))
    }

    fun `test nested helper analysis in same controller`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    respond(id);
                }

                private static void respond(Long id) {
                    if (id == null) {
                        notFound();
                    }
                    Object user = new Object();
                    render(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.HTML, info.kind)
        assertTrue(PlayResponsePresentation.tooltip(info).contains("May also return HTTP 404"))
    }

    fun `test nested helper analysis across project classes`() {
        myFixture.addFileToProject(
            "app/services/UserResponses.java",
            """
            package services;
            import play.mvc.Controller;
            public class UserResponses extends Controller {
                public static void writeJson(Object value) {
                    renderJSON(value);
                }
            }
            """.trimIndent()
        )
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import services.UserResponses;
            public class Users extends Controller {
                public static void show(Long id) {
                    Object user = new Object();
                    UserResponses.writeJson(user);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
    }

    fun `test nested helper cycle does not loop forever`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    first(id);
                }

                private static void first(Long id) {
                    second(id);
                }

                private static void second(Long id) {
                    if (id == null) {
                        first(1L);
                        return;
                    }
                    renderText("ok");
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.TEXT, info.kind)
    }

    fun `test helper throwing render json stays json`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import play.mvc.results.RenderJson;
            public class Users extends Controller {
                public static void show(Long id) {
                    respond(id);
                }

                private static void respond(Long id) {
                    throw new RenderJson(id);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
    }

    fun `test json remains primary with error branches`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import play.mvc.results.RenderJson;
            public class Users extends Controller {
                public static void show(Long id) {
                    if (id == null) {
                        badRequest();
                    }
                    if (id != null && id < 0) {
                        error(423, "locked");
                    }
                    respond(id);
                }

                private static void respond(Long id) {
                    throw new RenderJson(id);
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
        val tooltip = PlayResponsePresentation.tooltip(info)
        assertTrue(tooltip.contains("May also return HTTP 400"))
        assertTrue(tooltip.contains("May also return HTTP 423"))
    }

    fun `test json keeps success status in tooltip only`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            import play.mvc.results.RenderJson;
            public class Users extends Controller {
                public static void create() {
                    response.status = 201;
                    throw new RenderJson("ok");
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
        val tooltip = PlayResponsePresentation.tooltip(info)
        assertTrue(tooltip.contains("Nominal HTTP status: 201"))
        assertFalse(tooltip.contains("May also return HTTP 201"))
    }

    fun `test status only action falls back to http`() {
        val method = configureControllerAndFindMethod(
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void ping() {
                    ok();
                }
            }
            """.trimIndent()
        )

        val info = PlayActionResponseService.getInstance(project).analyze(method)
        assertEquals(PlayResponseKind.STATUS, info.kind)
        assertTrue(PlayResponsePresentation.tooltip(info).contains("Response: HTTP"))
    }

    fun `test route inlay uses nested helper outcome`() {
        myFixture.addFileToProject(
            "app/controllers/Users.java",
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    renderUser();
                }

                private static void renderUser() {
                    Object user = new Object();
                    renderJSON(user);
                }
            }
            """.trimIndent()
        )
        val routes = myFixture.addFileToProject(
            "conf/routes",
            """
            GET /users/{id} Users.show
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(routes.virtualFile)
        myFixture.doHighlighting()

        val inlays = myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength)
        assertTrue("expected a response inlay on routes", inlays.isNotEmpty())
    }

    fun `test route gets response inlay`() {
        myFixture.addFileToProject(
            "app/controllers/Users.java",
            """
            package controllers;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    Object user = new Object();
                    render(user);
                }
            }
            """.trimIndent()
        )
        val routes = myFixture.addFileToProject(
            "conf/routes",
            """
            GET /users/{id} Users.show
            """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(routes.virtualFile)
        myFixture.doHighlighting()

        val inlays = myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength)
        assertTrue("expected a response inlay on routes", inlays.isNotEmpty())
    }

    private fun configureControllerAndFindMethod(source: String): PsiMethod {
        val file = myFixture.addFileToProject("app/controllers/Users.java", source)
        return PsiTreeUtil.findChildOfType(file, PsiMethod::class.java) ?: error("method not found")
    }
}
