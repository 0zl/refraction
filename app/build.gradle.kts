plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val keystoreFile: String? by project
val keystorePassword: String? by project
val keyAliasProp: String? by project
val keyPasswordProp: String? by project

android {
    namespace = "shiro.refraction"
    compileSdk = 35

    defaultConfig {
        applicationId = "shiro.refraction"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.2"
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreFile?.let { file(it) }
            storePassword = keystorePassword
            keyAlias = keyAliasProp
            keyPassword = keyPasswordProp
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    implementation("androidx.webkit:webkit:1.12.1")
}
