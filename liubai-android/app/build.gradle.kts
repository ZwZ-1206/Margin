import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.liubai.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.liubai.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.8"
        androidResources.localeFilters += listOf("zh", "en")
    }

    signingConfigs {
        // 本地发布签名：从 keystore.properties 读取（该文件不入库）
        val props = Properties()
        val kp = rootProject.file("keystore.properties")
        if (kp.exists()) {
            kp.inputStream().use { props.load(it) }
        }
        if (props.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // 同时启用 v1 + v2 签名：国产 ROM（ColorOS/MIUI 等）从浏览器或
                // 文件管理器安装时，仅 v2 签名会被判定为「安装包已损坏」
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
