import com.android.build.api.dsl.ApplicationExtension
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

extensions.configure<ApplicationExtension> {
    namespace = "com.mochyy.mochymusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.mochymusic.kxmpzq"
        minSdk = 24
        targetSdk = 36
        versionCode = 73
        versionName = "8.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        val lastFmKey = System.getenv("LASTFM_API_KEY") ?: ""
        val lastFmSecret = System.getenv("LASTFM_SECRET") ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastFmSecret\"")

        fun libreFmStandIn(name: String) = MessageDigest.getInstance("MD5")
            .digest("$applicationId.librefm.$name".toByteArray()).joinToString("") { "%02x".format(it) }
        val libreFmKey = System.getenv("LIBREFM_API_KEY")?.takeIf { it.isNotEmpty() } ?: libreFmStandIn("key")
        val libreFmSecret = System.getenv("LIBREFM_SECRET")?.takeIf { it.isNotEmpty() } ?: libreFmStandIn("secret")
        buildConfigField("String", "LIBREFM_API_KEY", "\"$libreFmKey\"")
        buildConfigField("String", "LIBREFM_SECRET", "\"$libreFmSecret\"")

        val discordClientId = System.getenv("DISCORD_CLIENT_ID") ?: ""
        buildConfigField("String", "DISCORD_CLIENT_ID", "\"$discordClientId\"")
        manifestPlaceholders["discordRedirectScheme"] =
            if (discordClientId.isNotBlank()) "discord-$discordClientId" else "mochy-discord-unset"

        buildConfigField("Boolean", "IS_NIGHTLY", "false")
        buildConfigField("long", "BUILD_TIME_MILLIS", "1762819200000L")
        buildConfigField("Boolean", "CAST_AVAILABLE", "false")
        buildConfigField("String", "ARCHITECTURE", "\"universal\"")
    }

    // Do not remove or modify these signingConfigs. They are necessary for building, installing,
    // and updating Android apps in AI Studio.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isCrunchPngs = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
            // AI Studio.
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isCrunchPngs = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
            // AI Studio.
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = false
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!google_sans_flex.ttf:!sans_flex.ttf:!inter_black.ttf:!mochy_wrapped_v1.png:!mochy_wrapped_v2.png:!mochy_discord.png:!thank_you_badge.png:!wrapped_theme.mp3"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
                "**/libffmpegJNI.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xannotation-default-target=param-property"
        )
        suppressWarnings.set(false)
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    // implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    // debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.material3)
    // implementation(libs.androidx.adaptive)
    // implementation(libs.androidx.adaptive.layout)
    // implementation(libs.androidx.adaptive.navigation)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    // implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    // implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.ffmpeg)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)

    implementation(libs.room.runtime)
    // implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // implementation(libs.apache.lang3)

    implementation(libs.hilt)
    // implementation(libs.jsoup)
    ksp(libs.hilt.compiler)

    // Consolidated dependencies for all 16 providers and 5 modules
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp.dnsoverhttps)
    // implementation(libs.brotli)
    // implementation(libs.newpipeextractor)
    implementation(libs.json)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    // implementation(libs.smoothCorner)
    implementation(libs.lottie.compose)
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.appcheck.debug)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
