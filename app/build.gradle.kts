plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

android {
    namespace = "com.example.musicframe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.musicframe"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "1.0.32"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/release-key.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "amumusic123"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "amumusic123"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val appName = "AMuPtoFrame"
            val version = variant.versionName
            val buildType = variant.buildType.name
            output.outputFileName = "${appName}-v${version}-${buildType}.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Detekt static analysis configuration
detekt {
    config.setFrom(files("$rootDir/detekt-config.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}
