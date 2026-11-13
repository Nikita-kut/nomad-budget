import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val prodSupabaseUrl = "https://gncrjdgznnriuhyuatcv.supabase.co"
val prodSupabaseKey = "sb_publishable_Q9BgpKUePZpEYW-i5KofKg_shXWf7PL"
val nomadEnv = providers.gradleProperty("nomad.env").orElse(providers.environmentVariable("NOMAD_ENV")).orElse("prod")
val nomadSupabaseUrl = providers.environmentVariable("NOMAD_SUPABASE_URL").orElse(prodSupabaseUrl)
val nomadSupabaseKey = providers.environmentVariable("NOMAD_SUPABASE_KEY").orElse(prodSupabaseKey)
val nomadVersion = providers.gradleProperty("nomad.version").orElse(providers.environmentVariable("NOMAD_VERSION")).orElse("0.0.0")

fun storagePrefix(environment: String): String = if (environment == "prod") "nomad_budget:" else "nomad_budget_$environment:"

val generateAppConfig by tasks.registering {
    val env = nomadEnv
    val url = nomadSupabaseUrl
    val key = nomadSupabaseKey
    val version = nomadVersion
    val output = layout.buildDirectory.dir("generated/appConfig/kotlin")
    inputs.property("env", env)
    inputs.property("url", url)
    inputs.property("key", key)
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val environment = env.get()
        val supabaseUrl = url.get().trimEnd('/')
        val supabaseKey = key.get()
        val versionName = version.get().removePrefix("v")
        require(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(versionName)) { "nomad.version: ожидалась версия вида 0.32.0" }
        require(environment in setOf("prod", "staging", "local")) { "nomad.env: prod, staging или local, а не $environment" }
        require(Regex("^https?://[A-Za-z0-9.:-]+$").matches(supabaseUrl)) { "NOMAD_SUPABASE_URL: неверный адрес" }
        require(Regex("^[A-Za-z0-9._-]+$").matches(supabaseKey)) { "NOMAD_SUPABASE_KEY: неверный ключ" }
        require((environment == "prod") == (supabaseUrl == prodSupabaseUrl)) {
            "Сборка $environment указывает на ${if (supabaseUrl == prodSupabaseUrl) "прод" else "не прод"}: проверь NOMAD_ENV и NOMAD_SUPABASE_URL"
        }
        val prefix = storagePrefix(environment)
        val file = output.get().file("ru/nomadbudget/core/config/AppConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package ru.nomadbudget.core.config
            |
            |object AppConfig {
            |    const val ENVIRONMENT: String = "$environment"
            |    const val IS_PROD: Boolean = ${environment == "prod"}
            |    const val SUPABASE_URL: String = "$supabaseUrl"
            |    const val SUPABASE_KEY: String = "$supabaseKey"
            |    const val STORAGE_PREFIX: String = "$prefix"
            |    const val VERSION_NAME: String = "$versionName"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "ru.nomadbudget"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder { }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

tasks.named<ProcessResources>("wasmJsProcessResources") {
    val prefix = nomadEnv.map(::storagePrefix)
    inputs.property("storagePrefix", prefix)
    filesMatching("index.html") {
        filter { line -> line.replace("@STORAGE_PREFIX@", prefix.get()) }
    }
}
