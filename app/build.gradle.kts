import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "online.bus.bmarian.conexiuniclujmobile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Must match the watch app: the Wear data layer needs the same id and signing key.
        applicationId = "online.bmarian.bus"
        minSdk = 26
        targetSdk = 37
        // Phone 1xxxx, watch 2xxxx, then the release number, the same on both.
        versionCode = 10_030
        versionName = "1.24.0"

        buildConfigField("String", "API_BASE", "\"https://ceautobuziau.ro/api/\"")
        buildConfigField("String", "SITE_ORIGIN", "\"https://ceautobuziau.ro\"")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            (project.findProperty("apiBase") as String?)?.let { buildConfigField("String", "API_BASE", "\"$it\"") }
        }
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "**/libmaplibre.so"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val mapLibreSymbols = tasks.register<MapLibreSymbols>("mapLibreSymbols") {
    version = libs.versions.maplibre
    abis = android.defaultConfig.ndk.abiFilters
    ndkDirectory = androidComponents.sdkComponents.ndkDirectory
    cacheDirectory = File(gradle.gradleUserHomeDir, "maplibre-symbols")
}

androidComponents {
    // The baseline profile plugin leaves R8 on for this variant.
    finalizeDsl { android ->
        android.buildTypes.findByName("nonMinifiedRelease")?.optimization?.enable = false
    }
    onVariants(selector().withBuildType("release")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(mapLibreSymbols, MapLibreSymbols::outputDirectory)
    }
}

baselineProfile {
    dexLayoutOptimization = true
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // Already resolved at this version; declared so lint knows ActivityResult is safe.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.play.services.location)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.wearable)
    implementation(libs.play.app.update)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.zxing.core)
    implementation(libs.maplibre.android)
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
