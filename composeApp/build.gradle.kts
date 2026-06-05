import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip
import java.util.Properties

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val localEnvFile = rootProject.file(".env.local")
val localEnvProperties = Properties()
if (localEnvFile.exists()) {
    localEnvProperties.load(localEnvFile.inputStream())
}

fun envOrProperty(name: String, defaultValue: String = ""): String {
    val gradleKey = name.lowercase().split('_').let { parts ->
        parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
    return project.findProperty(gradleKey)?.toString()
        ?: System.getenv(name)
        ?: localEnvProperties.getProperty(name)
        ?: defaultValue
}

val appVersionName = project.findProperty("appVersion")?.toString() ?: libs.versions.app.version.get()
val appBuildNum = project.findProperty("appBuildNumber")?.toString()?.toIntOrNull() ?: libs.versions.app.buildNumber.get().toInt()
val crashReporterUrl = envOrProperty("CRASH_REPORTER_URL")
val crashReporterAppName = envOrProperty("CRASH_REPORTER_APP_NAME", "AniSurge")
val crashReporterApiKey = envOrProperty("CRASH_REPORTER_API_KEY")
val discordClientId = envOrProperty("DISCORD_CLIENT_ID")
val discordLargeImageKey = envOrProperty("DISCORD_LARGE_IMAGE_KEY", "logo")
val discordSmallImageKey = envOrProperty("DISCORD_SMALL_IMAGE_KEY", "play")
val releaseStoreFile = keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { file(it) }
val hasReleaseSigningConfig = listOf("keyAlias", "keyPassword", "storePassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() } && releaseStoreFile?.exists() == true

// Sanitize version for installers (e.g., "0.9.9-20260316" -> "0.9.9")
val numericVersion = appVersionName.split("-")[0].split("+")[0]
// Ensure it has 3 parts for Windows (e.g., "0.9" -> "0.9.0")
val windowsVersion = numericVersion.split(".").let {
    when (it.size) {
        1 -> "${it[0]}.0.0"
        2 -> "${it[0]}.${it[1]}.0"
        3 -> "${it[0]}.${it[1]}.${it[2]}"
        else -> "${it[0]}.${it[1]}.${it[2]}" // Take first 3
    }
}
// Linux RPM version (no dashes, dots only)
val linuxVersion = appVersionName.replace("-", ".")

// macOS DMG version: jpackage requires MAJOR > 0, so remap 0.x.y → 1.x.y
val macosVersion = numericVersion.split(".").let {
    val major = it[0].toIntOrNull() ?: 0
    val adjustedMajor = if (major == 0) 1 else major
    when (it.size) {
        1 -> "$adjustedMajor.0.0"
        2 -> "$adjustedMajor.${it[1]}.0"
        3 -> "$adjustedMajor.${it[1]}.${it[2]}"
        else -> "$adjustedMajor.${it[1]}.${it[2]}"
    }
}

// nav 2.9.0 → savedstate 1.3.6 → compose.ui:1.10.1 → skiko-awt:0.9.37.4 (JVM JAR)
// compose.desktop.currentOs:1.8.0 pins skiko-awt-runtime-linux-x64:0.8.18 (native .so)
// The 0.8.18 .so lacks glFlush() → UnsatisfiedLinkError at runtime.
// Force ALL skiko artifacts to the version the JVM JAR already resolved to.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.skiko") {
            useVersion("0.9.37.4")
            because("Align Skiko native runtime with JVM JAR version resolved by compose.ui:1.10.1")
        }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kmpAppIconGenerator)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget()
    jvm("desktop")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    // iOS targets are prepared but require commonMain JVM refactoring first.
    // See iosMain/ stubs and .github/workflows/build-release.yml for CI setup.
    // iosArm64()
    // iosSimulatorArm64()

    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Ktor HTTP client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            // DataStore for session persistence
            implementation(libs.datastore.preferences.core)
            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            // Date/time
            implementation(libs.kotlinx.datetime)
            // Glassmorphism blur
            implementation(libs.haze)
            // FileKit (cross-platform native folder picker)
            implementation(libs.filekit.dialogs.compose)
            // Okio (path support for DataStore)
            implementation(libs.okio)
        }

        androidMain.dependencies {
            implementation(libs.apng.core)
            implementation(libs.coil.gif)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.fragment:fragment:1.8.9")
            implementation(libs.backdrop)
            implementation(libs.kyant.shapes)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            // Native libmpv for Android (has ASS support via libass)
            implementation("dev.jdtech.mpv:libmpv:0.5.1")
            implementation("net.java.dev.jna:jna:5.14.0@aar")

            // MediaSession for earphone/headphone media button support
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.transformer)
            implementation(libs.rxffmpeg)

            // SAF Document access
            implementation("androidx.documentfile:documentfile:1.0.1")

            // QR generation and scanning for Android TV pairing
            implementation(libs.zxing.core)
            implementation(libs.zxing.android.embedded)

        }

        desktopMain.dependencies {
            implementation(libs.apng.core)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            // OkHttp engine for desktop HTTP — CIO's pure-Kotlin TLS stack fails
            // handshakes against Cloudflare-fronted hosts (e.g. the Anisurge BFF),
            // which surfaced as "Something went wrong" on signup/login. OkHttp uses
            // the JDK TLS stack and matches the Android engine that already works.
            implementation(libs.ktor.client.okhttp)
            // JNA for getting AWT canvas WID for MPV playback
            implementation(libs.jna)

            // JNativeHook for cross-platform global media keys (earphone play/pause)
            implementation(libs.jnativehook)
            // D-Bus is Linux-only. The native transport requires libc which crashes on Windows.
            if (System.getProperty("os.name").lowercase().contains("linux")) {
                implementation(libs.dbus.java)
            } else {
                compileOnly(libs.dbus.java)
            }
            implementation(libs.jave.all.deps)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

afterEvaluate {
    // Firebase Cloud Messaging (phone flavor only)
    val phoneConfigs = listOf(
        "phoneImplementation",
        "phoneDebugImplementation",
        "phoneReleaseImplementation"
    )
    phoneConfigs
        .filter { configurations.findByName(it) != null }
        .forEach { configName ->
            dependencies.add(configName, "com.google.firebase:firebase-messaging:24.1.0")
        }
}

buildConfig {
    packageName("to.kuudere.anisuge")
    buildConfigField("APP_VERSION", appVersionName)
    buildConfigField("APP_BUILD_NUMBER", appBuildNum)
    buildConfigField("CRASH_REPORTER_URL", crashReporterUrl)
    buildConfigField("CRASH_REPORTER_APP_NAME", crashReporterAppName)
    buildConfigField("CRASH_REPORTER_API_KEY", crashReporterApiKey)
    buildConfigField("DISCORD_CLIENT_ID", discordClientId)
    buildConfigField("DISCORD_LARGE_IMAGE_KEY", discordLargeImageKey)
    buildConfigField("DISCORD_SMALL_IMAGE_KEY", discordSmallImageKey)
}

android {
    namespace = "to.kuudere.anisuge"
    base.archivesName.set("anisurge")
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // Configure JVM target for Android
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Kotlin target options can also be here if using the newest KMP + AGP
    // but we'll stick to the androidTarget() registration above.

    defaultConfig {
        applicationId = "to.kuudere.anisuge"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appBuildNum
        versionName = appVersionName
        manifestPlaceholders["appLabel"] = "Anisurge"
        manifestPlaceholders["forceTvUi"] = "false"
        manifestPlaceholders["activityOrientation"] = "unspecified"
    }

    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
        }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
            manifestPlaceholders["appLabel"] = "Anisurge TV"
            manifestPlaceholders["forceTvUi"] = "true"
            manifestPlaceholders["activityOrientation"] = "landscape"
        }
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = releaseStoreFile
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = output.outputFileName.replace("composeApp-", "anisurge-")
        }
    }
}

val androidPermissionNamespace = "http://schemas.android.com/apk/res/android"

tasks.register("checkAndroidForegroundServicePermissions") {
    group = "verification"
    description = "Verifies Android foreground service declarations have matching manifest permissions."

    val manifestFile = layout.projectDirectory.file("src/androidMain/AndroidManifest.xml")
    inputs.file(manifestFile)

    doLast {
        val manifest = manifestFile.asFile
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)

        val declaredPermissions = mutableSetOf<String>()
        val permissionNodes = document.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val node = permissionNodes.item(i) as org.w3c.dom.Element
            val name = node.getAttributeNS(androidPermissionNamespace, "name")
            if (name.isNotBlank()) declaredPermissions += name
        }

        val typePermissions = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
        )

        val requiredPermissions = mutableSetOf<String>()
        val serviceNodes = document.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            val service = serviceNodes.item(i) as org.w3c.dom.Element
            val typeValue = service.getAttributeNS(androidPermissionNamespace, "foregroundServiceType")
            if (typeValue.isBlank() || typeValue == "none") continue

            requiredPermissions += "android.permission.FOREGROUND_SERVICE"
            typeValue.split('|')
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "none" && it != "shortService" }
                .forEach { type ->
                    val permission = typePermissions[type]
                        ?: error("Unknown foregroundServiceType '$type' in ${manifest.relativeTo(projectDir)}. Update checkAndroidForegroundServicePermissions if this is a valid new Android type.")
                    requiredPermissions += permission
                }
        }

        val missingPermissions = requiredPermissions - declaredPermissions
        check(missingPermissions.isEmpty()) {
            "AndroidManifest.xml is missing foreground service permission(s): ${missingPermissions.sorted().joinToString(", ")}"
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("checkAndroidForegroundServicePermissions")
}

// TV flavor does not use Firebase notifications; skip Google Services processing for TV variants.
tasks.matching { it.name.startsWith("processTv") && it.name.endsWith("GoogleServices") }.configureEach {
    enabled = false
}

compose {
    resources {
        packageOfResClass = "anisurge.composeapp.generated.resources"
    }
}

compose.desktop {
    application {
        mainClass = "to.kuudere.anisuge.MainKt"

        // Use Java 21 toolchain for running the application
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += listOf(
            "--add-opens=jdk.security.auth/com.sun.security.auth.module=ALL-UNNAMED",
            "--add-exports=jdk.security.auth/com.sun.security.auth.module=ALL-UNNAMED",
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            // Compose registers AppImage when the `linux { }` DSL runs — even on macOS CI hosts.
            // Gate formats AND platform blocks by host OS (see compose-multiplatform #3814).
            val hostOs = System.getProperty("os.name").lowercase()
            val onMac = "mac" in hostOs || "darwin" in hostOs
            val onLinux = "linux" in hostOs
            val onWindows = "win" in hostOs

            when {
                onMac -> targetFormats(TargetFormat.Dmg)
                onLinux -> targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
                onWindows -> targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            }

            packageName = "Anisurge"
            packageVersion = appVersionName
            description = "Anisurge — Multi-Platform Edition"
            copyright = "© 2026 Anisurge"
            vendor = "Anisurge"

            modules("jdk.security.auth", "jdk.crypto.ec", "java.desktop", "jdk.unsupported")

            if (onLinux) {
                linux {
                    iconFile.set(project.file("src/desktopMain/resources/logo.png"))
                    packageVersion = "${appVersionName.replace("-", ".")}.$appBuildNum"
                    shortcut = true
                    appCategory = "AudioVideo;Video;Entertainment;"
                }
            }

            if (onWindows) {
                windows {
                    iconFile.set(project.file("src/desktopMain/resources/logo.ico"))
                    packageVersion = windowsVersion
                    msiPackageVersion = windowsVersion
                    exePackageVersion = windowsVersion
                    // Per-user install avoids MSI 2318 ("file does not exist") when users lack admin rights.
                    perUserInstall = true
                    upgradeUuid = "d7e9b1a0-3f2d-4e9b-8a1c-5d6e7f8a9b0c"
                    shortcut = true
                    menu = true
                    menuGroup = "Anisurge"
                }
            }

            if (onMac) {
                macOS {
                    iconFile.set(project.file("src/desktopMain/resources/logo.icns"))
                    packageVersion = macosVersion
                    bundleID = "to.kuudere.anisuge"
                }
            }
        }
    }
}

tasks.register<Zip>("createPortableZip") {
    val osName = System.getProperty("os.name").lowercase()
    val platform = when {
        osName.contains("win") -> "windows"
        osName.contains("linux") -> "linux"
        osName.contains("mac") -> "macos"
        else -> "portable"
    }

    group = "compose desktop"
    description = "Creates a portable zip of the application"
    from("build/compose/binaries/main/app")
    archiveFileName.set("Anisurge-${appVersionName}.${appBuildNum}-${platform}-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    dependsOn("createDistributable")
    // Ensure this runs after other packaging tasks if they are in the graph to avoid implicit dependency warnings
    mustRunAfter(tasks.matching { it.name.startsWith("package") })
}
