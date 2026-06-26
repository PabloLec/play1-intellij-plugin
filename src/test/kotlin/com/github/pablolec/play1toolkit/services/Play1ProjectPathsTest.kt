package com.github.pablolec.play1toolkit.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame

class Play1ProjectPathsTest : BasePlatformTestCase() {

    fun testApplicationPathDoesNotTriggerPlayDetection() {
        myFixture.addFileToProject("conf/application.conf", "")
        myFixture.addFileToProject("conf/routes", "")
        myFixture.addFileToProject("app/controllers/Application.java", "class Application {}")

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
}
