plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.dogeapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.dogeapp"
        minSdk = 26
        targetSdk = 37
        // Every CI build gets a higher version code, so updates install over the old version.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 2
        versionName = "0.2.0"
    }

    // Fixed signing key so every build installs as an update over the previous one.
    // The key is NOT in the repo: CI decodes it from the GitHub secrets DOGE_KEYSTORE_BASE64 /
    // DOGE_KEYSTORE_PASSWORD into DOGE_KEYSTORE_FILE. Without it the normal debug key is used.
    val keystoreFile = System.getenv("DOGE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    val keystorePassword = System.getenv("DOGE_KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystoreFile != null && !keystorePassword.isNullOrEmpty()) {
            create("doge") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "doge"
                keyPassword = keystorePassword
            }
        }
    }
    val signing = signingConfigs.findByName("doge") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
