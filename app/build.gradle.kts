plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.crux.app"
    // compileSdk 36: current AndroidX needs it, and AGP 8.13.2 supports it. This is a
    // compile-time knob only; targetSdk stays 35 per build-guide.md (runtime behavior
    // is unchanged). See DECISIONS.log 2026-07-17.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crux.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room: export the schema from day one so migrations are testable.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Make the exported Room schemas available to the migration test (room-testing).
    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto) // phase 3: encrypted at-rest storage for the BYOK api key

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303") // real org.json for JVM unit tests (android stubs it)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.ui.tooling)
}
