import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProps = Properties().apply {
    listOf("local.properties", "signing.properties").forEach { name ->
        val propertiesFile = rootProject.file(name)
        if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
    }
}
fun v4Prop(key: String): String? =
    providers.environmentVariable(key).orNull ?: localProps.getProperty(key)

val signingFilePath = v4Prop("SIGNING_STORE_FILE")
val signingPassword = v4Prop("SIGNING_STORE_PASSWORD")
val signingAlias = v4Prop("SIGNING_KEY_ALIAS")
val signingKeyPassword = v4Prop("SIGNING_KEY_PASSWORD")
val signingInputsPresent = listOf(signingFilePath, signingPassword, signingAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }
val signingFile = signingFilePath?.let { file(it) }
val signingFileOutsideRepository = signingFile?.canonicalFile?.toPath()?.startsWith(
    rootDir.canonicalFile.toPath()
) == false

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "top.abdl_space.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.abdl_space.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val renderOutput = providers.systemProperty("v4.render.dir").orNull
    val projectRoot = rootDir.canonicalPath
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            if (renderOutput != null) {
                it.systemProperty("v4.render.dir", renderOutput)
                it.systemProperty("v4.project.root", projectRoot)
            }
            it.maxHeapSize = "512m"
            it.maxParallelForks = 1
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }

    signingConfigs {
        create("release") {
            if (signingInputsPresent && signingFileOutsideRepository) {
                storeFile = signingFile
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    val configured = signingInputsPresent
    val externalKey = signingFileOutsideRepository
    val keyFile = signingFile
    doLast {
        check(configured) { "Release requires all SIGNING_* properties; debug signing is never a fallback." }
        check(externalKey) { "SIGNING_STORE_FILE must point outside the repository." }
        check(keyFile?.isFile == true) { "Release keystore was not found." }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // Deterministic ViewModel scheduling for the messages state machine; version stays on the
    // shared coroutines BOM-free pin, test-only, no production dependency added.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
    testImplementation(libs.okhttp)
    testImplementation("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
