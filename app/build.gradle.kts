import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Required for @Serializable to actually generate serializers — without
    // this plugin the annotation is a silent no-op and ChatHistoryStore's
    // Json.encodeToString/decodeFromString calls throw SerializationException
    // at runtime (pre-existing bug, fixed alongside this feature since the
    // agent loop's tool schema depends on the same plugin).
    id("org.jetbrains.kotlin.plugin.serialization")
}

// API keys for the agent's three cloud calls (Claude, Maya TTS, Sarvam STT)
// live in local.properties — gitignored, never committed — matching how
// sdk.dir already works there. Guarded so CI/a fresh checkout without the
// file still builds; each client fails loudly at runtime on an empty key
// rather than surfacing a bare 401. These BuildConfig strings are trivially
// extractable from a built APK — fine for development, not for a shipping
// build (a shipping app needs a server-side proxy; out of scope for now).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun apiKey(propertyName: String): String = localProperties.getProperty(propertyName, "")

android {
    namespace = "com.saarthi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saarthi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${apiKey("anthropic.apiKey")}\"")
        buildConfigField("String", "MAYA_API_KEY", "\"${apiKey("maya.apiKey")}\"")
        buildConfigField("String", "SARVAM_API_KEY", "\"${apiKey("sarvam.apiKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // SaarthiInteractionSession hosts a ComposeView outside any Activity —
    // VoiceInteractionSession provides none of the ViewModelStoreOwner /
    // SavedStateRegistryOwner scaffolding an Activity normally gives Compose
    // for free, so the session supplies both itself using these.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Raw HTTP client for the three cloud calls (Claude, Maya TTS, Sarvam
    // STT) — no Retrofit/Moshi/Gson: one of those responses is raw binary
    // audio and one is multipart, so a converter-factory layer doesn't fit
    // cleanly, and kotlinx-serialization (above) already covers JSON.
    // Deliberately no logging-interceptor, even for debug: the Claude
    // request body carries the full serialized screen of whatever app the
    // user is in (order contents, phone numbers) — logging that to logcat
    // would be a PII leak in a takeover app.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
