plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "com.shanhai.yaozhi"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.shanhai.yaozhi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        create("release") {
            // TODO(生产发布): 替换为真实 keystore 路径 + 密码
            // 暂时复用 debug keystore，等正式发布时用 keytool -genkeypair 生成
            storeFile = file("../keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.coreKtx)
                implementation(libs.coroutines)
                // Compose BOM
                implementation(platform("androidx.compose:compose-bom:2024.09.03"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.material3:material3")
                implementation("androidx.activity:activity-compose")
                // osmdroid — OpenStreetMap 原生地图 SDK（Apache 2.0，无需 key）
                implementation("org.osmdroid:osmdroid-android:6.1.20")
            }
        }
    }
}