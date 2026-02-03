# Установка и настройка окружения

## 1. Системные требования

### Операционная система
- **Windows**: 10 или 11 (64-bit)
- **macOS**: 10.14 (Mojave) или новее
- **Linux**: Ubuntu 18.04 LTS или эквивалент

### Аппаратные требования
- **RAM**: минимум 8 GB, рекомендуется 16 GB
- **Диск**: 10 GB свободного места
- **CPU**: Intel i5 или эквивалент

### Программное обеспечение
- **Android Studio**: Electric Eel (2022.1.1) или новее
- **Android SDK**: API 24 (Android 7.0) - API 34 (Android 14)
- **JDK**: версия 11 (рекомендуется AdoptOpenJDK или Azul Zulu)
- **Gradle**: 8.0+ (включён в проект)

## 2. Установка JDK 11

### Windows

1. Скачайте JDK 11 от [Azul Zulu](https://www.azul.com/downloads/?package=jdk#zulu) или [AdoptOpenJDK](https://adoptium.net/)
2. Установите в каталог (например, `C:\Program Files\Java\jdk-11`)
3. Настройте переменные окружения:

```powershell
# В PowerShell (от администратора)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-11", "Machine")
[System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Java\jdk-11\bin", "Machine")
```

4. Проверьте установку:

```powershell
java -version
# Должно вывести: openjdk version "11.x.x"
```

### macOS

```bash
# Используя Homebrew
brew install openjdk@11

# Добавьте в ~/.zshrc или ~/.bash_profile
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
export PATH=$JAVA_HOME/bin:$PATH
```

### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install openjdk-11-jdk

# Проверка
java -version
```

## 3. Установка Android Studio

1. Скачайте с [официального сайта](https://developer.android.com/studio)
2. Запустите установщик
3. При первом запуске выберите **Standard Setup**
4. Дождитесь установки Android SDK

### Настройка Android SDK

В Android Studio:
1. Откройте **Tools → SDK Manager**
2. Установите следующие компоненты:
   - **SDK Platforms**: API 24, 28, 33, 34
   - **SDK Tools**:
     - Android SDK Build-Tools 34.0.0
     - Android SDK Platform-Tools
     - Android Emulator (опционально)

3. Настройте переменную окружения `ANDROID_SDK_ROOT`:

```powershell
# Windows
$env:ANDROID_SDK_ROOT = "C:\Users\<ваш_пользователь>\AppData\Local\Android\Sdk"
```

## 4. Клонирование проекта

```bash
cd C:\Users\<ваш_пользователь>\AndroidStudioProjects
git clone <URL_репозитория> Meshtastic
cd Meshtastic
```

Или откройте существующий проект в Android Studio:
**File → Open → выберите папку Meshtastic**

## 5. Разрешения Android

### Необходимые разрешения в AndroidManifest.xml

```xml
<!-- BLE сканирование (Android 12+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />

<!-- BLE подключение (Android 12+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- BLE старые устройства -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- Геолокация для BLE сканирования -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Фоновый сервис -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### Почему нужна геолокация для BLE?

Android требует разрешение на геолокацию для сканирования BLE устройств, т.к. BLE может использоваться для определения местоположения (beacon'ы).

## 6. Сборка проекта

### Из командной строки

```powershell
# Windows PowerShell
cd C:\Users\<user>\AndroidStudioProjects\Meshtastic
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug

# Результат: app/build/outputs/apk/debug/app-debug.apk
```

### Из Android Studio

1. Откройте проект
2. Дождитесь завершения Gradle Sync
3. Выберите **Build → Make Project** (Ctrl+F9)
4. Для запуска: **Run → Run 'app'** (Shift+F10)

### Проверка сборки

```powershell
# Список всех задач Gradle
.\gradlew.bat tasks

# Запуск тестов
.\gradlew.bat :app:testDebugUnitTest

# Проверка зависимостей
.\gradlew.bat :app:dependencies
```

## 7. Подключение физического устройства

### Включение режима разработчика

#### Android 8.0+
1. Откройте **Настройки → О телефоне**
2. Нажмите **Номер сборки** 7 раз
3. Вернитесь назад и откройте **Параметры разработчика**
4. Включите **Отладка по USB**

### Подключение по USB

1. Подключите устройство к компьютеру
2. Разрешите отладку на телефоне (всплывающее окно)
3. Проверьте подключение:

```powershell
# Должно показать ваше устройство
adb devices
```

### Установка APK

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 8. Проверка BLE на устройстве

### Проверка поддержки BLE

```powershell
adb shell pm list features | findstr bluetooth
# Должно вывести: android.hardware.bluetooth_le
```

### Проверка состояния Bluetooth

```powershell
adb shell settings get global bluetooth_on
# 1 = включён, 0 = выключен
```

## 9. Подготовка устройства Meshtastic

### Требования к устройству
- Meshtastic прошивка версии 2.0+
- BLE включён в настройках устройства
- Устройство в пределах 10 метров

### Проверка UUID сервиса

Meshtastic использует UUID сервиса: `6ba1b218-15a8-461f-9fa8-5dcae273eafd`

Для сканирования:
```powershell
# Android BLE scanner (требует отдельное приложение)
# Или используйте nRF Connect от Nordic Semiconductor
```

## 10. Частые проблемы при установке

### Gradle sync failed

**Проблема**: `Could not resolve dependencies`

**Решение**:
1. Проверьте интернет-соединение
2. Очистите кэш Gradle:
```powershell
.\gradlew.bat clean
rm -r $env:USERPROFILE\.gradle\caches
```

### JAVA_HOME not set

**Проблема**: `ERROR: JAVA_HOME is not set`

**Решение**:
```powershell
# Временно в текущей сессии
$env:JAVA_HOME = "C:\Program Files\Java\jdk-11"

# Постоянно (требует перезапуск PowerShell)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-11", "User")
```

### Android SDK not found

**Проблема**: SDK не найден

**Решение**: Создайте `local.properties` в корне проекта:
```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

### BLE не работает в эмуляторе

**Проблема**: Эмулятор не поддерживает BLE

**Решение**: Используйте физическое устройство Android 7.0+

## 11. Первый запуск

После успешной сборки и установки:

1. Убедитесь, что Bluetooth включён на телефоне
2. Разрешите приложению доступ к BLE и геолокации
3. Включите устройство Meshtastic
4. Запустите приложение
5. Перейдите на вкладку **"Подключение"**
6. Нажмите **"Поиск устройств"**

Ожидаемый результат: устройство Meshtastic появится в списке.

## 12. Дополнительные инструменты

### ADB (Android Debug Bridge)

```powershell
# Просмотр логов приложения
adb logcat | Select-String "Meshtastic"

# Очистка данных приложения
adb shell pm clear com.example.meshtastic

# Отправка файла на устройство
adb push file.txt /sdcard/
```

### Logcat фильтры

```powershell
# Только ошибки
adb logcat *:E

# Только BLE логи
adb logcat | Select-String "BleManager"

# Сохранить в файл
adb logcat > logcat.txt
```

## 13. Обновление зависимостей

Проверьте актуальные версии в `app/build.gradle.kts`:

```kotlin
dependencies {
    // Protobuf
    implementation("com.google.protobuf:protobuf-javalite:3.25.1")
    
    // Material Design
    implementation("com.google.android.material:material:1.12.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
}
```

## 14. Следующие шаги

После успешной установки:
- Изучите `USAGE.md` для работы с приложением
- Ознакомьтесь с `ARCHITECTURE.md` для понимания структуры
- Прочитайте `TROUBLESHOOTING.md` при возникновении проблем
