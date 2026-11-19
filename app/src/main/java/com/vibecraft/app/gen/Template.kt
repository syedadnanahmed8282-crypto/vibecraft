package com.vibecraft.app.gen

/** Build files added around the AI's code so the repository builds an APK on GitHub Actions. */
object Template {
    fun files(appName: String, projectId: String): Map<String, String> {
        val label = appName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'").replace("\"", "\\\"")
        val appId = "com.vibecraft.p" + projectId.filter { it.isLetterOrDigit() }.take(8).lowercase()
        return linkedMapOf(
            "settings.gradle.kts" to SETTINGS,
            "build.gradle.kts" to ROOT,
            "gradle.properties" to PROPS,
            ".gitignore" to GITIGNORE,
            "app/build.gradle.kts" to appGradle(appId),
            "app/src/main/AndroidManifest.xml" to MANIFEST,
            "app/src/main/res/values/strings.xml" to "<resources>\n    <string name=\"app_name\">$label</string>\n</resources>\n",
            "app/src/main/res/values/themes.xml" to THEMES,
            "app/src/main/java/com/vibecraft/generated/MainActivity.kt" to MAIN,
            "app/src/main/java/com/vibecraft/generated/AppRoot.kt" to APPROOT,
            ".github/workflows/build.yml" to WORKFLOW,
        )
    }

    private val SETTINGS = """
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "GeneratedApp"
include(":app")
""".trimStart()

    private val ROOT = """
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
""".trimStart()

    private val PROPS = """
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
""".trimStart()

    private val GITIGNORE = """
.gradle/
build/
local.properties
.idea/
*.iml
""".trimStart()

    private fun appGradle(appId: String) = """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vibecraft.generated"
    compileSdk = 35

    defaultConfig {
        applicationId = "$appId"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
""".trimStart()

    private val MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Generated">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimStart()

    private val THEMES = """
<resources>
    <style name="Theme.Generated" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
""".trimStart()

    private val MAIN = """
package com.vibecraft.generated

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}
""".trimStart()

    private val APPROOT = """
package com.vibecraft.generated

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppRoot() {
    MaterialTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your app will appear here")
        }
    }
}
""".trimStart()

    private val WORKFLOW = """
name: Build APK
on:
  push:
    branches: [main]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.10.2
      - name: Build debug APK
        run: gradle assembleDebug --no-daemon --stacktrace
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: vibecraft-debug-apk
          path: app/build/outputs/apk/debug/*.apk
""".trimStart()
}
