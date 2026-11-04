plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "vn.guidinghand"
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.guidinghand"
        manifestPlaceholders["appLabel"] = "GuidingHand"
        minSdk = 30
        targetSdk = 36
        versionCode = 70
        versionName = "1.5.61"
        buildConfigField("int", "DEV_BACKEND_PORT", "8766")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("candidateBuild").orNull == "true") {
                applicationIdSuffix = ".candidate"
                versionNameSuffix = "-candidate"
                buildConfigField("int", "DEV_BACKEND_PORT", "8768")
                manifestPlaceholders["appLabel"] = "GuidingHand Candidate"
                resValue("string", "app_name", "GuidingHand Candidate")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
