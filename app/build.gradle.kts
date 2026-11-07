plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rfcontrol.app"
    compileSdk = 35

    defaultConfig {
        // Fresh package ID avoids a signature conflict with earlier debug builds.
        // Preserve old installs and their local history; this app starts fresh.
        applicationId = "com.kays.rfcontrol.privacyshield"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
