import kotlin.collections.plusAssign

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrainsKotlinSerialization)
}

android {
    namespace = "ar.edu.um.tif.aiAssistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.edu.um.tif.aiAssistant"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/arm64-v8a/libjnidispatch.so"
            pickFirsts += "lib/armeabi-v7a/libjnidispatch.so"
            pickFirsts += "lib/x86/libjnidispatch.so"
            pickFirsts += "lib/x86_64/libjnidispatch.so"
        }
    }
    configurations.all {
        resolutionStrategy {
            force(libs.vosk.android)
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.runtime.livedata)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.kotlin.serialization)
    implementation(libs.aimybox.core)
    implementation(libs.aimybox.components)
    implementation(libs.aimybox.google.platform.speechkit)
    implementation(libs.aimybox.dummy.api)
    implementation(libs.aimybox.kaldi.speechkit) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.vosk.android)
//    {
//        exclude(group = "net.java.dev.jna", module = "jna")
//    }
//    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
