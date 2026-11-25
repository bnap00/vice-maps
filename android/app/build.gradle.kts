import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Optional overrides, from -P flags or ~/.gradle/gradle.properties (see README).
fun setting(name: String, default: String) = (findProperty(name) as String?) ?: default

// Optional release key: android/keystore.properties (never committed).
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "dev.vicemaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vicemaps"
        // Android Auto's Car App Library needs API 29+.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Phones (arm64) + the x86_64 emulator; drops ~100 MB of unused native libraries.
        buildConfigField("String", "VALHALLA_URL",
            "\"${setting("viceMaps.valhallaUrl", "https://valhalla1.openstreetmap.de/route")}\"")
        buildConfigField("String", "PHOTON_URL",
            "\"${setting("viceMaps.photonUrl", "https://photon.komoot.io/api/")}\"")
        buildConfigField("String", "USER_AGENT",
            "\"${setting("viceMaps.userAgent", "ViceMaps/$versionName (Android; open source)")}\"")
        // `-PphoneOnly` builds arm64 only: the smaller APK for the phone.
        ndk {
            abiFilters += if (project.hasProperty("phoneOnly")) listOf("arm64-v8a")
                          else listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Your own key when keystore.properties exists, otherwise the local debug key,
            // which is fine for installing on your own phone.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.car.app)

    implementation(libs.ferrostar.core)
    implementation(libs.ferrostar.car.app)
    implementation(libs.ferrostar.gms)
    implementation(libs.ferrostar.ui.compose)
    implementation(libs.ferrostar.ui.maplibre)
    implementation(libs.ferrostar.ui.maplibre.car.app)

    implementation(libs.maplibre.compose)
    implementation(libs.car.app.compose)
    implementation(libs.play.services.location)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.junit)
}
