plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    val signingProps = file("../signing.properties")
    val commitHash = runCatching {
        providers.exec {
            workingDir = rootDir
            commandLine = "git rev-parse --short HEAD".split(" ")
        }.standardOutput.asText.get().trim()
    }.getOrDefault("nogit")
    val commitSubject = runCatching {
        providers.exec {
            workingDir = rootDir
            commandLine = "git log -1 --pretty=%s".split(" ")
        }.standardOutput.asText.get().trim()
    }.getOrDefault("")

    namespace = "com.aistra.hail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aistra.hail"
        minSdk = 26
        targetSdk = 37
        versionCode = 10000
        versionName = "雹-GKD-1.0.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-g$commitHash"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!commitSubject.startsWith("[release]")) versionNameSuffix = "-g$commitHash"
            signingConfig = if (signingProps.exists()) {
                val props = `java.util`.Properties().apply { load(signingProps.reader()) }
                signingConfigs.create("release") {
                    storeFile = file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            } else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    androidResources {
        generateLocaleConfig = true
        // Do not compress the dex files, so the apk can be imported as a privileged app
        noCompress += "dex"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":gkd-feature"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.pinyin4j)
    implementation(libs.material)
    implementation(libs.insetter)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.dhizuku.api)
    implementation(libs.appiconloader)
    implementation(libs.compose.preference)
    implementation(libs.commons.text)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lsposed.hiddenapibypass)
    compileOnly(libs.xposed)
}
