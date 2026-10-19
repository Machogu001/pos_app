import java.util.Properties

fun isReleasePackagingBuild(): Boolean {
    return gradle.startParameter.taskNames.any { task ->
        val normalized = task.lowercase()
        ("assemble" in normalized || "bundle" in normalized) && "release" in normalized
    }
}

fun resolveVersionCode(): Int {
    val versionFile = rootProject.file("version.properties")
    if (!versionFile.exists()) {
        versionFile.writeText("POS_APP_VERSION_CODE=1\n")
    }

    val content = versionFile.readText()
    val regex = Regex("""POS_APP_VERSION_CODE\s*=\s*(\d+)""")
    val match = regex.find(content)
        ?: error("POS_APP_VERSION_CODE entry not found in ${versionFile.path}")

    val current = match.groupValues[1].toInt()
    if (!isReleasePackagingBuild()) {
        return current
    }

    val next = current + 1
    val updated = content.replaceFirst(regex, "POS_APP_VERSION_CODE=$next")

    if (updated != content) {
        versionFile.writeText(updated)
        println("versionCode bumped for release build: $current -> $next")
    }

    return next
}

val resolvedVersionCode = resolveVersionCode()

plugins {
    id("com.android.application")
}

// Release signing: keystore.properties (never committed) in the project root points to the keystore.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "co.ke.bremac.posapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.ke.bremac.posapp"
        minSdk = 26
        targetSdk = 37
        versionCode = resolvedVersionCode
        versionName = "2.12.5"
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
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
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}


