plugins {
    alias(libs.plugins.android.application)
    id("com.google.protobuf") version "0.9.4"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                // Kotlin DSL: create builtin "java" and set lite
                create("java") {
                    option("lite")
                }
            }
        }
    }
}



android {
    namespace = "com.example.meshtastic"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.meshtastic"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Существующие зависимости
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("com.google.android.material:material:1.12.0")
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // Protobuf (lite для Android)
    implementation("com.google.protobuf:protobuf-javalite:3.25.1")

    // Карты (выбери один вариант)
    implementation("org.maplibre.gl:android-sdk:10.2.0")
    // ИЛИ
    // implementation("org.osmdroid:osmdroid-android:6.1.17")

    // UI списков
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Тестирование
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}