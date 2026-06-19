import os

def create_file(path, content):
    dir_name = os.path.dirname(path)
    if dir_name:  # Only create directory if path has a directory
        os.makedirs(dir_name, exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

# Root files
create_file('build.gradle', '''// Top-level build file
plugins {
    id 'com.android.application' version '8.2.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
''')

create_file('settings.gradle', '''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AnimatedKeyboard"
include ':app'
''')

create_file('gradle.properties', '''org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
''')

create_file('.gitignore', '''*.iml
.gradle
/local.properties
/.idea/
.DS_Store
/build''')

create_file('app/build.gradle', '''plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.animatedkeyboard'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.animatedkeyboard"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
}
''')

create_file('app/proguard-rules.pro', '# ProGuard\n')

create_file('app/src/main/AndroidManifest.xml', '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AnimatedKeyboard">        <service
            android:name=".service.AnimatedKeyboardIME"
            android:exported="true"
            android:label="@string/keyboard_name"
            android:permission="android.permission.BIND_INPUT_METHOD">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
    </application>
</manifest>
''')

create_file('app/src/main/res/values/strings.xml', '''<resources>
    <string name="app_name">Animated Keyboard</string>
    <string name="keyboard_name">Animated Keyboard</string>
</resources>
''')

create_file('app/src/main/res/xml/method.xml', '''<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <subtype
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="keyboard" />
</input-method>
''')

print("Files created successfully!")
