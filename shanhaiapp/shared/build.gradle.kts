plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCocoapods)
}
android {
    namespace = "com.shanhai.core"
    compileSdk = 34
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = 26
    }
}
kotlin {
    // ===== Android & JVM（Windows 上全功能）=====
    androidTarget()
    jvm()

    // ===== iOS targets（Windows 上 SKIPPED，macOS 上自动启用）=====
    iosX64()       // iOS Simulator（Intel Mac）
    iosArm64()     // iOS Device + iOS Simulator（Apple Silicon）

    // sourceSets
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.serializationJson)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // ===== 平台 sourceSets（各 target 的 actual 实现）=====
        val androidMain by getting {
            dependencies {
                // osmdroid 6.1.20 — OpenStreetMap 原生地图（Apache 2.0，无 key）
                implementation("org.osmdroid:osmdroid-android:6.1.20")
            }
        }
        val jvmMain by getting {
            // JVM 端（jvmTest 跑），无额外依赖
        }

        // iosMain — iOS 平台 actual 实现（Windows 上 SKIPPED，macOS 上启用）
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
        }
    }

    // ===== cocoapods + framework 导出 =====
    cocoapods {
        summary = "ShanHai KMP shared — engines, data, state"
        homepage = "https://example.com/shanhai"   // TODO: 上线改 GitHub Pages
        version = "0.1.0"
        name = "ShanHaiKmp"
        framework {
            baseName = "ShanHaiKmp"
            isStatic = true
        }
    }
}