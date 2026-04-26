import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip
import java.util.Properties

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val appVersionName = project.findProperty("appVersion")?.toString() ?: libs.versions.app.version.get()
val appBuildNum = project.findProperty("appBuildNumber")?.toString()?.toIntOrNull() ?: libs.versions.app.buildNumber.get().toInt()

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
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.rxffmpeg)
            
            // Native libmpv for Android (has ASS support via libass)
            implementation("dev.jdtech.mpv:libmpv:0.5.1")
            implementation("net.java.dev.jna:jna:5.14.0@aar")
            
            // MediaSession for earphone/headphone media button support
            implementation(libs.androidx.media3.session)

            // SAF Document access
            implementation("androidx.documentfile:documentfile:1.0.1")

            // QR generation and scanning for Android TV pairing
            implementation(libs.zxing.core)
            implementation(libs.zxing.android.embedded)

            // Firebase Cloud Messaging
            implementation("com.google.firebase:firebase-messaging:24.1.0")
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            // CIO engine for desktop HTTP
            implementation(libs.ktor.client.cio)
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

buildConfig {
    packageName("to.kuudere.anisuge")
    buildConfigField("APP_VERSION", appVersionName)
    buildConfigField("APP_BUILD_NUMBER", appBuildNum)
}

android {
    namespace = "to.kuudere.anisuge"
    setProperty("archivesBaseName", "anisurge")
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
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Anisurge"
            packageVersion = appVersionName
            description = "Anisurge — Multi-Platform Edition"
            copyright = "© 2026 Anisurge"
            vendor = "Anisurge"

            modules("jdk.security.auth", "java.sql", "java.naming", "jdk.crypto.ec", "java.desktop", "java.management", "jdk.unsupported")

            linux {
                iconFile.set(project.file("src/desktopMain/resources/logo.png"))
                // Combine version and build number for Linux (e.g., 0.9.9.12)
                packageVersion = "${appVersionName.replace("-", ".")}.$appBuildNum"
                
                shortcut = true
                appCategory = "AudioVideo;Video;Entertainment;"
            }

            windows {
                iconFile.set(project.file("src/desktopMain/resources/logo.ico"))
                // MSI version must be MAJOR.MINOR.BUILD (max 3 segments)
                packageVersion = windowsVersion
                upgradeUuid = "d7e9b1a0-3f2d-4e9b-8a1c-5d6e7f8a9b0c" // Stable UUID for updates
                
                shortcut = true
                menu = true
                menuGroup = "Anisurge"
                
                // Note: Signing is now handled via project properties in the CI workflow
                // by passing -Pcompose.desktop.signing.sign=true etc.
                // This avoids DSL compilation issues.
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
