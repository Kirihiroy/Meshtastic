# Зависимости для проекта Meshtastic

## Необходимые зависимости для добавления в `app/build.gradle.kts`

### 1. AndroidX и Lifecycle (для MVVM)

```kotlin
implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")
implementation("androidx.fragment:fragment:1.6.2")
```

### 2. Room Database (для локального хранения сообщений)

```kotlin
val roomVersion = "2.6.1"
implementation("androidx.room:room-runtime:$roomVersion")
annotationProcessor("androidx.room:room-compiler:$roomVersion")
// Для Kotlin используй kapt вместо annotationProcessor
```

### 3. Protobuf (для парсинга пакетов Meshtastic)

**Вариант A: Использовать официальную библиотеку meshtastic-java (если доступна)**

```kotlin
// Проверь актуальную версию на GitHub
implementation("com.github.meshtastic:meshtastic-java:latest-version")
```

**Вариант B: Использовать Protobuf напрямую**

```kotlin
implementation("com.google.protobuf:protobuf-java:3.25.1")
```

И добавь плагин в начало `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.protobuf") version "0.9.4"
}
```

### 4. Карты

**Вариант A: MapLibre GL Android (рекомендуется, современный)**

```kotlin
implementation("org.maplibre.gl:android-sdk:10.2.0")
```

**Вариант B: OsmDroid (проще для офлайн-карт)**

```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.17")
```

### 5. Location Services

```kotlin
implementation("com.google.android.gms:play-services-location:21.0.1")
```

### 6. Navigation Component (опционально, для навигации между экранами)

```kotlin
val navVersion = "2.7.6"
implementation("androidx.navigation:navigation-fragment:$navVersion")
implementation("androidx.navigation:navigation-ui:$navVersion")
```

### 7. Coroutines (опционально, но рекомендуется для асинхронных операций)

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
```

**Примечание**: Если используешь только Java, можешь обойтись без Coroutines, используя Thread/Handler.

---

## Пример полного блока dependencies (для Java проекта)

```kotlin
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
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    
    // Protobuf
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    
    // Карты (выбери один вариант)
    implementation("org.maplibre.gl:android-sdk:10.2.0")
    // ИЛИ
    // implementation("org.osmdroid:osmdroid-android:6.1.17")
    
    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Тестирование
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
```

---

## Разрешения для AndroidManifest.xml

Добавь в `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- Для Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- Для Android 10+ (API 29+) -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Для Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Для интернета (если нужен для загрузки тайлов карты) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Где найти актуальные версии

- **AndroidX**: https://developer.android.com/jetpack/androidx/versions
- **Room**: https://developer.android.com/jetpack/androidx/releases/room
- **Protobuf**: https://github.com/protocolbuffers/protobuf/releases
- **MapLibre**: https://github.com/maplibre/maplibre-gl-native/releases
- **OsmDroid**: https://github.com/osmdroid/osmdroid/releases
- **Play Services**: https://developers.google.com/android/guides/setup
