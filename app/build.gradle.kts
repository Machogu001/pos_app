plugins {
    id("com.android.application")
}

android {
    namespace = "co.ke.bremac.posapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.ke.bremac.posapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.5.0"
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    }
