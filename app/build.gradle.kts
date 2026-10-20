import java.util.Properties
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releasePropertiesFile = rootProject.file(".signing/release.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.exists()) releasePropertiesFile.inputStream().use { load(it) }
}

abstract class LegalAssets : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val prepareLegalAssets = tasks.register<LegalAssets>("prepareLegalAssets") {
    from(rootProject.file("legal")) { into("legal") }
    into("legal") {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("THIRD_PARTY_NOTICES.txt"))
    }
    outputDirectory.set(layout.buildDirectory.dir("generated/legalAssets"))
    into(outputDirectory)
}

android {
    namespace = "pro.dasoft.ownsonic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pro.dasoft.ownsonic"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releasePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releasePropertiesFile.exists()) signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(prepareLegalAssets, LegalAssets::outputDirectory)
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
