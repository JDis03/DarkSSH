import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.darkssh.client"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.darkssh.client"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.sshd.core)
    implementation(libs.sshd.sftp)
    implementation(libs.spongycastle.core)
    implementation(libs.spongycastle.prov)
    implementation(libs.termlib)
    implementation(libs.conscrypt.android)
    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sshlib) {
        // Excluir tink base — en Android usamos tink-android (incluye las mismas clases)
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.cbssh.sshlib) {
        // cbssh trae tink base además de tink-android — excluir el base para evitar duplicados
        exclude(group = "com.google.crypto.tink", module = "tink")
    }

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.conscrypt.openjdk.uber)
    testImplementation(libs.testcontainers)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.assertj.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}

// Force a single tink-android version across all dependencies.
// sshlib pulls tink 1.8.0, cbssh pulls tink 1.22.0 — we keep 1.22.0 (cbssh needs it).
// We do NOT exclude tink-android globally because androidx.security.crypto depends on it.
configurations.all {
    resolutionStrategy {
        force("com.google.crypto.tink:tink-android:1.22.0")
    }
}

// === Docker-based SFTP integration tests ===
//
// Tests tagged with the DockerIntegrationTest marker (JUnit4 @Category) spin
// up a real OpenSSH server via Testcontainers and exercise SftpClient2/cbssh
// end-to-end against it. They require a running Docker daemon, so they are
// EXCLUDED from the regular unit test tasks (testDebugUnitTest,
// testReleaseUnitTest) that `./gradlew test` and `./init.sh` invoke — those
// must keep working in environments without Docker.
//
// Run them explicitly with: ./gradlew integrationTest
val dockerIntegrationCategory = "com.darkssh.client.transport.cbssh.DockerIntegrationTest"

// Only exclude from the AGP-generated unit test tasks by exact name — NOT via
// tasks.withType<Test>().configureEach, which would also match (and break)
// the dedicated `integrationTest` task registered below that needs the
// opposite filter (includeCategories).
listOf("testDebugUnitTest", "testReleaseUnitTest").forEach { taskName ->
    tasks.matching { it.name == taskName }.configureEach {
        (this as Test).useJUnit {
            excludeCategories(dockerIntegrationCategory)
        }
    }
}

afterEvaluate {
    val unitTestTask = tasks.named<Test>("testDebugUnitTest").get()
    tasks.register<Test>("integrationTest") {
        group = "verification"
        description =
            "Runs Docker-based SFTP integration tests against a real OpenSSH " +
            "server (requires a running Docker daemon). Not part of " +
            "'./gradlew test' or './init.sh'."
        testClassesDirs = unitTestTask.testClassesDirs
        classpath = unitTestTask.classpath
        useJUnit {
            includeCategories(dockerIntegrationCategory)
        }
        // docker-java (used internally by Testcontainers) can default to an
        // old Docker API version (1.32) that recent Docker Engine releases
        // (28+) reject outright ("client version too old"). Pin a modern
        // version explicitly so version negotiation succeeds. Must be the
        // "api.version" JAVA SYSTEM PROPERTY (docker-java reads this exact
        // key, not the DOCKER_API_VERSION env var the docker CLI uses).
        systemProperty("api.version", "1.45")
    }
}
