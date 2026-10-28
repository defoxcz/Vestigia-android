import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingFile = file(System.getenv("VESTIGIA_ANDROID_SIGNING_PROPERTIES")
    ?: "${System.getProperty("user.home")}/.config/vestigia-android/release-signing.properties")
val releaseSigning = Properties().apply {
    if (signingFile.isFile) signingFile.inputStream().use { load(it) }
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appBuildNumber = appVersion.getProperty("buildNumber")?.toIntOrNull()
    ?: error("version.properties must contain an integer buildNumber")
require(appBuildNumber in 1..9999) { "buildNumber must be between 1 and 9999" }
val appVersionName = "1.0.${appBuildNumber.toString().padStart(4, '0')}"

android {
    namespace = "com.vestigiascriptorium.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.vestigiascriptorium.android"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = appBuildNumber
        versionName = appVersionName
    }
    signingConfigs {
        if (signingFile.isFile) create("permanentRelease") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["applicationLabel"] = "Vestigia Scriptorium Debug"
        }
        release {
            applicationIdSuffix = ".release"
            isDebuggable = false
            manifestPlaceholders["applicationLabel"] = "Vestigia Scriptorium Release"
            if (signingFile.isFile) signingConfig = signingConfigs.getByName("permanentRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
