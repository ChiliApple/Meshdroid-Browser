import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// ---------------------------------------------------------------------------
// Release-Signing
//
// Die Keystore-Daten kommen entweder aus keystore.properties (lokal, gitignored)
// oder aus Umgebungsvariablen (CI). Fehlen beide, wird der Release-Build
// unsigniert erzeugt - der Build bricht NICHT ab.
//
// Gelesen wird ueber die providers-API und nicht ueber File/System.getenv:
// nur so registriert Gradle die Werte als Build-Eingaben. Andernfalls wuerde
// die Configuration Cache eine alte "kein Keystore"-Konfiguration
// weiterverwenden, nachdem keystore.properties angelegt wurde.
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    providers.fileContents(
        rootProject.layout.projectDirectory.file("keystore.properties")
    ).asText.orNull?.let { load(it.reader()) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envKey).orNull?.takeIf { it.isNotBlank() }

val ksPath = signingValue("storeFile", "KEYSTORE_FILE")
val ksPassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val ksAlias = signingValue("keyAlias", "KEY_ALIAS")
val ksAliasPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasSigningConfig = ksPath != null && rootProject.file(ksPath).exists() &&
    ksPassword != null && ksAlias != null && ksAliasPassword != null

android {
    namespace = "com.chiliapple.meshdroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chiliapple.meshdroid"
        minSdk = 29
        targetSdk = 36
        versionCode = (providers.environmentVariable("VERSION_CODE").orNull ?: "1").toInt()
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0.0"

        resourceConfigurations += listOf("en", "de")
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(ksPath!!)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksAliasPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    dependenciesInfo {
        // Keine verschluesselten Dependency-Metadaten in der APK (nur fuer Play relevant)
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.biometric)
    implementation(libs.google.material)
}
