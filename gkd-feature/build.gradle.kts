plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.remap)
    alias(libs.plugins.codeorigin)
}

android {
    namespace = "li.gkd.app"

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // Embedded in Hail: never advertise or install a standalone GKD APK.
        manifestPlaceholders["channel"] = "embedded"
        manifestPlaceholders["buildKey"] = ""
        manifestPlaceholders["commitId"] = "f6c42e2f0a8926df295d33f6f086748424dc3111"
        manifestPlaceholders["commitTime"] = "1788713442"
        manifestPlaceholders["tagName"] = ""
        resValue("bool", "is_accessibility_tool", "true")
    }

    buildFeatures {
        compose = true
        aidl = true
        resValues = true
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

    packaging.jniLibs.useLegacyPackaging = true
    packaging.resources.excludes += setOf(
        "META-INF/**",
        "DebugProbesKt.bin",
    )
}

composeCompiler {
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("stability_config.conf"),
    )
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(":gkd-db"))
    implementation(project(":selector"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.concurrent.futures)

    remapApi(project(":hidden-api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.priv.kit.ui)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.reorderable)
    implementation(libs.androidx.splashscreen)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable)
    implementation(libs.exp4j)
    implementation(libs.toaster)
    implementation(libs.permissions)
    implementation(libs.device)
    implementation(libs.json5)
    compileOnly(libs.codeorigin)
    implementation(libs.kevinnzouWebview) {
        exclude(group = "com.google.android.material", module = "material")
    }
}
