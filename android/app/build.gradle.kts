import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
}

val releaseKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val debugKeystore = providers.environmentVariable("WARPSCOUT_DEBUG_KEYSTORE")
val versionNameValue = providers.environmentVariable("WARPSCOUT_VERSION_NAME").orElse("0.1.0-dev")
val versionCodeValue = providers.environmentVariable("WARPSCOUT_VERSION_CODE").map(String::toInt).orElse(1)
val upstreamTag = providers.environmentVariable("WARPSCOUT_UPSTREAM_TAG").orElse("v0.14.0")
val upstreamCommit = providers.environmentVariable("WARPSCOUT_UPSTREAM_COMMIT").orElse("2fe3507")

android {
    namespace = "io.github.openwarpkit.warpscout"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.github.openwarpkit.warpscout"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeValue.get()
        versionName = versionNameValue.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPSTREAM_TAG", "\"${upstreamTag.get()}\"")
        buildConfigField("String", "UPSTREAM_COMMIT", "\"${upstreamCommit.get()}\"")
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (debugKeystore.isPresent) {
            getByName("debug") {
                storeFile = file(debugKeystore.get())
            }
        }
        if (releaseKeystore.isPresent) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                keyAlias = releaseKeyAlias.get()
                storePassword = releaseStorePassword.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            if (releaseKeystore.isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    val mobileAar = file("libs/warpscout.aar")
    if (mobileAar.isFile) {
        implementation(files(mobileAar))
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
