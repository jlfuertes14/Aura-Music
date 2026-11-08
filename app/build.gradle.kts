plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.auramusic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.auramusic.kotlin"
        minSdk = 26
        targetSdk = 36

        // Dynamic version code & name: CI build run number or Gradle parameter with local defaults
        val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val propVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull()
        versionCode = propVersionCode ?: ciVersionCode ?: 41

        versionName = "1.5.0"
        val propVersionName = project.findProperty("versionName") as? String
        val envVersionName = System.getenv("APP_VERSION_NAME")
        if (propVersionName != null) {
            versionName = propVersionName
        } else if (envVersionName != null) {
            versionName = envVersionName
        }

        vectorDrawables {
            useSupportLibrary = true
        }

        // Read .env file for Supabase subscription pool credentials
        var supabasePublicUrl = ""
        var supabaseAnonKey = ""
        val envFile = rootProject.file(".env")
        if (envFile.exists()) {
            envFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key == "SUPABASE_PUBLIC_URL") supabasePublicUrl = value
                    if (key == "SUPABASE_ANON_KEY") supabaseAnonKey = value
                }
            }
        }
        if (supabasePublicUrl.isEmpty()) {
            supabasePublicUrl = System.getenv("SUPABASE_PUBLIC_URL") ?: ""
        }
        if (supabaseAnonKey.isEmpty()) {
            supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY") ?: ""
        }

        buildConfigField("String", "SUPABASE_PUBLIC_URL", "\"$supabasePublicUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "aurarelease2026"
                keyAlias = "auramusic"
                keyPassword = "aurarelease2026"
            }
        }
    }

    buildTypes {
        debug {
            val keystoreFile = rootProject.file("keystore/release.keystore")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            val keystoreFile = rootProject.file("keystore/release.keystore")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            // Kuromoji includes duplicate documentation files in its core and IPADIC jars.
            excludes += "META-INF/*.md"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "aura-music-v${variant.versionName}-universal.apk"
            }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // SimpMusic Media3 Playback & ResolvingDataSource + SimpleCache Stack
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.common)

    // SimpMusic PipePipeExtractor + NewPipeExtractor + OkHttp
    implementation(libs.pipepipe.extractor)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Artwork & Dynamic Palette Extraction
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)

    // Kotlin Coroutines & JSON Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Dictionary-backed kanji readings keep Japanese romaji lyrics complete.
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    // Firebase Cloud Messaging & In-App Push Notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
