import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Never store the upload key or passwords in this repository or CI logs.
// When unset, Gradle may build an unsigned release for analysis; distribution requires this file.
val uploadSigning = providers.environmentVariable("PLOI_PANEL_SIGNING_PROPERTIES").orNull?.let { path ->
    val source = file(path)
    require(source.isFile) { "Upload signing properties file is missing" }
    Properties().apply { source.inputStream().use { load(it) } }.also { properties ->
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { field ->
            require(!properties.getProperty(field).isNullOrBlank()) { "Missing upload signing field: $field" }
        }
        require(file(properties.getProperty("storeFile")).isFile) { "Upload keystore is missing" }
    }
}

android {
    namespace = "com.qrcommunication.ploipanel"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qrcommunication.ploipanel"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadSigning != null) create("upload") {
            storeFile = file(uploadSigning.getProperty("storeFile"))
            storePassword = uploadSigning.getProperty("storePassword")
            keyAlias = uploadSigning.getProperty("keyAlias")
            keyPassword = uploadSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (uploadSigning != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Both bundled locales must remain available for the in-app language switcher.
    bundle {
        language {
            enableSplit = false
        }
    }
    testOptions {
        // Robolectric Compose tests render real screens on the JVM (resources, locales, dialogs).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.maxHeapSize = "3g"
            // Live screenshot tour: opt-in only (-Pscreenshots), needs network and a read-only token
            // file named by PLOI_SCREENSHOT_TOKEN_FILE. Never part of the default test/CI run.
            if (!project.hasProperty("screenshots")) test.exclude("**/screenshots/**")
            else test.systemProperty("ploi.screenshots.out", layout.buildDirectory.dir("screenshots").get().asFile.path)
        }
    }
    packaging {
        jniLibs {
            // Upstream AndroidX binary is already packaged without strippable symbols.
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

// A public release must never silently fall back to an unsigned APK or AAB.
val requireUploadSigning = tasks.register("requireUploadSigning") {
    doLast { check(uploadSigning != null) { "Set PLOI_PANEL_SIGNING_PROPERTIES before building a release" } }
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(requireUploadSigning)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    // SSH transport: connection-time host key probe and interactive shell sessions.
    implementation("com.github.mwiede:jsch:2.28.7")
    // JSch's Bouncy Castle providers give Ed25519/X25519 on every supported API level (JCA only
    // exposes them on recent Android releases).
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // Outlined Material Symbols for navigation, states and list items. Version comes from the BOM;
    // R8 strips every unused icon from release builds, so only referenced vectors ship.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Hosts ComponentActivity for Compose UI tests; debug-only, never in release.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    // In-process SSH server: end-to-end tests of host key checks, auth and PTY shell.
    testImplementation("org.apache.sshd:sshd-core:2.20.0")
    testImplementation("net.i2p.crypto:eddsa:0.3.0")
    testImplementation("org.slf4j:slf4j-nop:2.0.17")
    // JVM Compose UI tests: reproduce crashes and input limits without a device.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
