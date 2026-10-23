plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    compileOnly("com.lagradost:cloudstream3:pre-release")
}
