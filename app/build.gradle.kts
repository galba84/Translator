plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sereda.translator"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sereda.translator"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Engines use extracted files, so compress bundled models to reduce install/update staging space.
    packaging { jniLibs.excludes += listOf("**/x86/**", "**/armeabi-v7a/**") }
}

dependencies {
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.12.20.aar"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val verifyBundledResources by tasks.registering {
    doLast {
        check(file("src/main/assets/resources.json").exists()) {
            "Bundled models are missing. Run powershell -ExecutionPolicy Bypass -File scripts/prepare-resources.ps1 from the project root."
        }
        check(file("libs/sherpa-onnx-static-link-onnxruntime-1.12.20.aar").exists()) {
            "Offline speech library is missing. Run scripts/prepare-resources.ps1."
        }
        val manifest = file("src/main/assets/resources.json").readText()
        val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        check(paths.isNotEmpty()) { "Resource manifest is empty. Run scripts/prepare-resources.ps1." }
        val translationPairs = file("src/main/assets/models/translation").listFiles().orEmpty()
            .filter { it.isDirectory }.map { it.name }.toSet()
        check(translationPairs == setOf("en-es", "es-en")) {
            "Unexpected translation models. Run scripts/prepare-resources.ps1 to prepare the supported language pair."
        }
        paths.forEach { path ->
            check(file("src/main/assets/$path").isFile) { "Bundled resource missing: $path. Run scripts/prepare-resources.ps1." }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyBundledResources) }
