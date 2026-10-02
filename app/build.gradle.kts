import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

fun apiBaseUrl(propertyName: String, environmentName: String, fallback: String): String {
    val raw = providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))
        .orElse(fallback)
        .get()
        .trim()
    return if (raw.endsWith("/")) raw else "$raw/"
}

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localApiBaseUrl = apiBaseUrl(
    propertyName = "BOOKING_API_LOCAL_URL",
    environmentName = "BOOKING_API_LOCAL_URL",
    fallback = "http://10.0.2.2:8080/"
)
val stagingApiBaseUrl = apiBaseUrl(
    propertyName = "BOOKING_API_STAGING_URL",
    environmentName = "BOOKING_API_STAGING_URL",
    fallback = "https://staging.booking-hotel.invalid/"
)
val productionApiBaseUrl = apiBaseUrl(
    propertyName = "BOOKING_API_PROD_URL",
    environmentName = "BOOKING_API_PROD_URL",
    fallback = "https://api.booking-hotel.invalid/"
)

fun configValue(name: String, fallback: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse(fallback)
        .get()

val appVersionCode = configValue("BOOKING_VERSION_CODE", "1").toInt()
val appVersionName = configValue("BOOKING_VERSION_NAME", "1.0.0")

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.bookinghotel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bookinghotel"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", quotedBuildConfig(localApiBaseUrl))
            buildConfigField("String", "ENVIRONMENT", quotedBuildConfig("local"))
            buildConfigField("boolean", "DEMO_PAYMENTS_ENABLED", "true")
        }

        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "API_BASE_URL", quotedBuildConfig(stagingApiBaseUrl))
            buildConfigField("String", "ENVIRONMENT", quotedBuildConfig("staging"))
            buildConfigField("boolean", "DEMO_PAYMENTS_ENABLED", "true")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "API_BASE_URL", quotedBuildConfig(productionApiBaseUrl))
            buildConfigField("String", "ENVIRONMENT", quotedBuildConfig("production"))
            buildConfigField("boolean", "DEMO_PAYMENTS_ENABLED", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Validate only variants being built, so local/debug development needs no remote URL.
fun validateRemoteUrl(value: String, propertyName: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid")) {
        "$propertyName must be set to the actual HTTPS backend URL for this environment"
    }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "$propertyName must not contain credentials, a query, or a fragment"
    }
}
val validateStagingBackend = tasks.register("validateStagingBackend") {
    doLast {
        validateRemoteUrl(stagingApiBaseUrl, "BOOKING_API_STAGING_URL")
        require(stagingApiBaseUrl != productionApiBaseUrl) { "Staging must not use the production backend URL" }
    }
}
val validateProductionBackend = tasks.register("validateProductionBackend") {
    doLast { validateRemoteUrl(productionApiBaseUrl, "BOOKING_API_PROD_URL") }
}
tasks.matching { it.name == "preStagingBuild" }.configureEach { dependsOn(validateStagingBackend) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateProductionBackend) }
