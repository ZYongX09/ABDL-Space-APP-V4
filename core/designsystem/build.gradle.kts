import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "top.abdl_space.app.core.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // API26/28/33 instrumented android-all jars are also resolved from ~/.m2 by Robolectric.
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.maxHeapSize = "512m"
            it.maxParallelForks = 1
        }
    }
    // RuntimeShader/miuix entry points must stay out of API26-compatible facade methods in release.
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.miuix.ui)
    api(libs.miuix.icons)
    implementation(libs.miuix.blur)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // Module-owned test dependencies; do not change the shared catalog for this isolated scope.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
