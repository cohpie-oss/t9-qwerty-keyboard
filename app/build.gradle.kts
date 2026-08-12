plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

layout.buildDirectory.set(File(System.getProperty("java.io.tmpdir"), "t9-qwerty-build/app"))

android { namespace = "com.t9qwerty.keyboard"; compileSdk = 35
    defaultConfig { applicationId = "com.t9qwerty.keyboard"; minSdk = 23; targetSdk = 35; versionCode = 5; versionName = "1.1.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
