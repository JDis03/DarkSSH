buildscript {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.findLibrary("kotlin-gradle-plugin").get())
        classpath(libs.findLibrary("ksp-gradle-plugin").get())
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

spotless {
    ratchetFrom = "origin/main"

    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.8.0")
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.5.8"))
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }

    format("misc") {
        target(listOf("**/*.md", "**/.gitignore"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}