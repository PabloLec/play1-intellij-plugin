package com.github.pablolec.play1toolkit.run

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class Play1JavaEnvironmentResolverTest : BasePlatformTestCase() {

    fun `test resolves project sdk into java process environment`() {
        val javaHome = currentJdkHome()
        val sdk = JavaSdk.getInstance().createJdk("play-test-jdk-${System.nanoTime()}", javaHome)
        registerProjectSdk(sdk)

        try {
            val environment = Play1JavaEnvironmentResolver.resolve(project, project.basePath!!)

            assertNotNull(environment)
            assertEquals(javaHome, environment!!.sdkHomePath)
            assertEquals(javaHome, environment.env["JAVA_HOME"])
            val pathEntry = environment.env.entries.singleOrNull { it.key.equals("PATH", ignoreCase = true) }
            assertNotNull(pathEntry)
            assertTrue(
                pathEntry!!.value.split(File.pathSeparator).first() == File(javaHome, "bin").path
            )
        } finally {
            unregisterProjectSdk(sdk)
        }
    }

    private fun registerProjectSdk(sdk: Sdk) {
        WriteAction.runAndWait<Exception> {
            ProjectJdkTable.getInstance().addJdk(sdk)
            ProjectRootManager.getInstance(project).projectSdk = sdk
        }
    }

    private fun unregisterProjectSdk(sdk: Sdk) {
        WriteAction.runAndWait<Exception> {
            if (ProjectRootManager.getInstance(project).projectSdk == sdk) {
                ProjectRootManager.getInstance(project).projectSdk = null
            }
            ProjectJdkTable.getInstance().removeJdk(sdk)
        }
    }

    private fun currentJdkHome(): String {
        val rawHome = System.getProperty("java.home")
        val home = File(rawHome)
        return if (home.name == "jre") {
            home.parentFile.absolutePath
        } else {
            home.absolutePath
        }
    }
}
