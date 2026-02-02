import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)

    // Necessário para ler google-services.json e gerar default_web_client_id
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.jack.meuholerite"
    compileSdk = 35
    
    // Define a versão do NDK para garantir a extração correta dos símbolos de depuração
    // Se não tiver uma versão específica instalada, o Gradle baixará a padrão para o seu SDK.
    ndkVersion = "26.1.10909125" 

    // Carregar propriedades da chave
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        val releaseKeyFile = rootProject.file("release-key.jks")
        if (releaseKeyFile.exists()) {
            create("release") {
                storeFile = releaseKeyFile
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String?
                keyAlias = keystoreProperties["KEY_ALIAS"] as String?
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String?
            }
        }
    }

    defaultConfig {
        applicationId = "com.jack.meuholerite"
        minSdk = 24
        targetSdk = 35
        versionCode = 23
        versionName = "1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Habilita a ofuscação e redução de código (R8)
            isMinifyEnabled = true
            
            // Remove recursos (drawables, layouts) não utilizados
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }

            // Inclui símbolos de depuração nativos no App Bundle (.aab)
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
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

    // Biometric
    implementation(libs.androidx.biometric)

    // Image Loading
    implementation(libs.coil.compose)

    // Google AdMob
    implementation(libs.play.services.ads)

    // PDF Library
    implementation(libs.pdfbox.android)

    // JSON Serialization
    implementation(libs.gson)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Google Login / Credentials Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    // Gemini AI
    implementation(libs.google.generativeai)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
