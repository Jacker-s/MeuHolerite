import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.jack.meuholerite"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }
    val groqApiKey = (keystoreProperties.getProperty("groq.api.key") ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    signingConfigs {
        val releaseKeyFile = File("C:/Users/Jackson/Documents/release-key")
        if (releaseKeyFile.exists()) {
            create("release") {
                storeFile = releaseKeyFile
                storePassword = "Samsung000@"
                keyAlias = "key0"
                keyPassword = "Samsung000@"
            }
        }
    }

    defaultConfig {
        applicationId = "com.jack.meuholerite"
        minSdk = 24
        targetSdk = 35
        versionCode = 75
        versionName = "2.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,INDEX.LIST}"
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
        force("androidx.browser:browser:1.8.0")
        force("io.grpc:grpc-api:1.62.2")
        force("io.grpc:grpc-core:1.62.2")
        force("io.grpc:grpc-okhttp:1.62.2")
        force("io.grpc:grpc-stub:1.62.2")
        force("io.grpc:grpc-android:1.62.2")
        force("io.grpc:grpc-protobuf-lite:1.62.2")
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    // Dependências do Android, Compose e Firebase
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.coil.compose)
    implementation(libs.play.services.ads)
    implementation(libs.pdfbox.android)
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    
    // Ktor dependencies for Groq AI
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidbrowserhelper)
    implementation(libs.androidx.ui.graphics)

    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.webkit)
    implementation(libs.app.update.ktx)
    implementation(libs.google.android.material)
    
    // Google Play Services Core (Resolving crash/missing R$string)
    implementation(libs.playservices.base)
    implementation(libs.playservices.basement)
    
    // Google Drive Backup
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)
    implementation(libs.play.review.ktx)
    implementation(libs.billing.ktx)

    // Resolve gRPC conflict between Google Drive and Firestore
    implementation("io.grpc:grpc-okhttp:1.62.2")
    implementation("io.grpc:grpc-android:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
    implementation("io.grpc:grpc-api:1.62.2")

    // Dependências de Teste
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
