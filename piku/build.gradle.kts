import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun loadSigningProps(): Properties {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    return props
}

val signingProps = loadSigningProps()

fun resolveVersionName(): String {
    val ciRef = System.getenv("GITHUB_REF_NAME")
    if (!ciRef.isNullOrBlank() && ciRef.startsWith("v")) {
        return ciRef.removePrefix("v")
    }
    return providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
        workingDir(rootProject.projectDir)
        isIgnoreExitValue = true
    }
        .standardOutput.asText
        .map { it.trim().removePrefix("v").ifBlank { "0.1.0" } }
        .get()
}

fun versionNameToCode(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 }) * 10000 + (parts.getOrElse(1) { 0 }) * 100 + parts.getOrElse(2) { 0 }
}

val appVersionName = resolveVersionName()
val appVersionCode = versionNameToCode(appVersionName)

android {
    namespace = "com.piku.client"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.piku.client"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystorePath = signingProps.getProperty("piku.keystore.path")
            if (keystorePath != null && file(keystorePath).exists()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystorePath)
                    storePassword = signingProps.getProperty("piku.keystore.password")
                    keyAlias = signingProps.getProperty("piku.key.alias")
                    keyPassword = signingProps.getProperty("piku.key.password")
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // JVM 单测中 android.util.Log 等 Android API 返回默认值而非抛 "not mocked"
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}