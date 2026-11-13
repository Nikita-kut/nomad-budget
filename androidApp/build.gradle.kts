plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val nomadEnv = providers.gradleProperty("nomad.env").orElse(providers.environmentVariable("NOMAD_ENV")).orElse("prod").get()
val nomadVersion = providers.gradleProperty("nomad.version").orElse(providers.environmentVariable("NOMAD_VERSION")).orElse("0.0.0").get()
val versionParts = Regex("^v?(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(nomadVersion)?.destructured?.toList()?.map(String::toInt)
    ?: error("nomad.version: ожидалась версия вида 0.32.0 или v0.32.0, а не $nomadVersion")
val keystorePath = providers.environmentVariable("NOMAD_KEYSTORE_PATH").orNull

android {
    namespace = "ru.nomadbudget.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ru.nomadbudget"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = maxOf(1, versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2])
        versionName = versionParts.joinToString(".")
        if (nomadEnv != "prod") {
            applicationIdSuffix = ".$nomadEnv"
            versionNameSuffix = "-$nomadEnv"
        }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("NOMAD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("NOMAD_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("NOMAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
