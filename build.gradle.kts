import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val pluginVersionProvider = providers.gradleProperty("pluginVersion")
val pluginChangeNotesProvider = providers.gradleProperty("pluginChangeNotes")
    .orElse("<p>See the GitHub release for details.</p>")

plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
}

group = providers.gradleProperty("pluginGroup").get()
version = pluginVersionProvider.get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.yaml")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = pluginVersionProvider
        changeNotes = pluginChangeNotesProvider
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            providers.gradleProperty("pluginUntilBuild").orNull?.let { untilBuild = it }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").orElse("9.5.1").get()
    }
}
