package com.github.pablolec.play1toolkit.playjpa

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.github.pablolec.play1toolkit.response.PlayActionResponseAnalyzer
import com.github.pablolec.play1toolkit.response.PlayResponseKind
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiField
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayJpaSupportTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        addJavaStub(
            "javax/persistence/Entity.java",
            """
            package javax.persistence;
            public @interface Entity {}
            """
        )
        addJavaStub(
            "javax/persistence/Id.java",
            """
            package javax.persistence;
            public @interface Id {}
            """
        )
        addJavaStub(
            "javax/persistence/OneToMany.java",
            """
            package javax.persistence;
            public @interface OneToMany { String mappedBy() default ""; }
            """
        )
        addJavaStub(
            "javax/persistence/ManyToOne.java",
            """
            package javax.persistence;
            public @interface ManyToOne {}
            """
        )
        addJavaStub(
            "play/db/jpa/JPABase.java",
            """
            package play.db.jpa;
            public class JPABase {}
            """
        )
        addJavaStub(
            "play/db/jpa/GenericModel.java",
            """
            package play.db.jpa;
            public class GenericModel extends JPABase {}
            """
        )
        addJavaStub(
            "play/db/jpa/Model.java",
            """
            package play.db.jpa;
            public class Model extends GenericModel {
                public Long id;
            }
            """
        )
        addJavaStub(
            "play/mvc/Controller.java",
            """
            package play.mvc;
            public class Controller {
                public static void render(Object... args) {}
                public static void renderJSON(Object value) {}
            }
            """
        )
    }

    private fun addJavaStub(path: String, source: String) {
        myFixture.addFileToProject(path, source.trimIndent())
    }

    private fun addProjectFile(path: String, source: String) =
        myFixture.addFileToProject(path, source.trimIndent())

    fun `test model detection and relations`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            import javax.persistence.Entity;
            import javax.persistence.OneToMany;
            import play.db.jpa.Model;
            import java.util.List;

            @Entity
            public class User extends Model {
                public String email;
                public String name;
                @OneToMany(mappedBy = "user")
                public List<Order> orders;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/models/Order.java",
            """
            package models;
            import javax.persistence.Entity;
            import javax.persistence.ManyToOne;
            import play.db.jpa.Model;

            @Entity
            public class Order extends Model {
                @ManyToOne
                public User user;
                public String reference;
            }
            """.trimIndent()
        )

        val service = PlayJpaModelService.getInstance(project)
        val user = service.findModelByName("User")
        assertNotNull(user)
        assertEquals("email", user!!.fields.first().name)
        assertTrue(user.relations.any { it.fieldName == "orders" && it.targetModel == "Order" })
    }

    fun `test app models convention does not treat dto and services as jpa models`() {
        addProjectFile(
            "app/models/actualites/ActualitesDTO.java",
            """
            package models.actualites;

            public class ActualitesDTO {
                public String title;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/models/advancedsearch/services/impl/AdvancedSearchQueryBuilderSrv.java",
            """
            package models.advancedsearch.services.impl;

            public class AdvancedSearchQueryBuilderSrv {
                public String build() { return "x"; }
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/models/legacy/LegacyFallbackModel.java",
            """
            package models.legacy;

            public class LegacyFallbackModel {
                public Long id;
                public String name;
            }
            """.trimIndent()
        )

        val service = PlayJpaModelService.getInstance(project)
        val modelNames = service.getAllModels().map { it.className }.toSet()
        assertFalse(modelNames.contains("ActualitesDTO"))
        assertFalse(modelNames.contains("AdvancedSearchQueryBuilderSrv"))
        assertTrue(modelNames.contains("LegacyFallbackModel"))
    }

    fun `test finder string reference resolves to model field`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            import javax.persistence.Entity;
            import play.db.jpa.Model;

            @Entity
            public class User extends Model {
                public String email;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/controllers/Users.java",
            """
            package controllers;
            import models.User;
            public class Users {
                public void show(String email) {
                    User.find("byEma<caret>il", email).first();
                }
            }
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("app/controllers/Users.java"))
        val file = myFixture.file

        val target = file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertNotNull(target)
        assertEquals("email", (target as PsiField).name)
    }

    fun `test yaml fixture utilities and relations work on play fixtures`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            import javax.persistence.Entity;
            import play.db.jpa.Model;

            @Entity
            public class User extends Model {
                public String email;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/models/Order.java",
            """
            package models;
            import javax.persistence.Entity;
            import javax.persistence.ManyToOne;
            import play.db.jpa.Model;

            @Entity
            public class Order extends Model {
                @ManyToOne
                public User user;
            }
            """.trimIndent()
        )

        addProjectFile(
            "conf/data.yml",
            """
            User(bob):
              email: bob@example.com
            
            Order(order1):
              user: bob
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("conf/data.yml"))
        val yaml = myFixture.file as YAMLFile
        val topMapping = yaml.documents.first().topLevelValue as YAMLMapping
        val userFixture = topMapping.keyValues.first { it.keyText == "User(bob)" }
        val userMapping = userFixture.value as YAMLMapping
        val emailKv = userMapping.keyValues.first { it.keyText == "email" }
        assertEquals("User", PlayYamlFixtureUtils.getModelNameFromKey(userFixture))
        assertEquals(listOf("bob"), PlayYamlFixtureUtils.getAllAliasesForModel(yaml, "User"))
        assertEquals("email", emailKv.keyText)

        val orderFixture = topMapping.keyValues.first { it.keyText == "Order(order1)" }
        val orderMapping = orderFixture.value as YAMLMapping
        val userRelationKv = orderMapping.keyValues.first { it.keyText == "user" }
        assertEquals("bob", userRelationKv.valueText.trim())
        val orderModel = PlayJpaModelService.getInstance(project).findModelByName("Order")
        assertNotNull(orderModel)
        assertTrue(orderModel!!.relations.any { it.fieldName == "user" && it.targetModel == "User" })
    }

    fun `test template variable resolver keeps jpa model type information`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            import javax.persistence.Entity;
            import play.db.jpa.Model;

            @Entity
            public class User extends Model {
                public String email;
                public String name;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/controllers/Users.java",
            """
            package controllers;
            import models.User;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    User user = new User();
                    renderTemplate("Users/show.html", user);
                }
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/views/Users/show.html",
            """
            <div>${'$'}{user.email}</div>
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("app/views/Users/show.html"))

        val resolver = PlayTemplateVariableResolver.getInstance(project)
        val model = PlayJpaModelService.getInstance(project).findModelByName("User")
        assertNotNull(model)
        val variableType = JavaPsiFacade.getElementFactory(project).createType(model!!.psiClass)
        val target = resolver.resolveMember(myFixture.file, variableType, "email", false)
        assertNotNull(target)
        assertEquals("email", (target as PsiField).name)
    }

    fun `test render json on jpa model keeps typed response details`() {
        addProjectFile(
            "app/models/User.java",
            """
            package models;
            import javax.persistence.Entity;
            import play.db.jpa.Model;

            @Entity
            public class User extends Model {
                public String email;
            }
            """.trimIndent()
        )
        addProjectFile(
            "app/controllers/Users.java",
            """
            package controllers;
            import models.User;
            import play.mvc.Controller;
            public class Users extends Controller {
                public static void show(Long id) {
                    User user = new User();
                    renderJSON(user);
                }
            }
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("app/controllers/Users.java"))
        val controller = myFixture.file

        val method = (controller as com.intellij.psi.PsiJavaFile).classes.single().methods.single()
        val info = PlayActionResponseAnalyzer(project).analyze(method)
        assertEquals(PlayResponseKind.JSON, info.kind)
        assertTrue(info.outcomes.single().details?.contains("JSON<User>") == true)
    }
}
