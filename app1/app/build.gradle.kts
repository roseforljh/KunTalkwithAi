configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
    exclude(group = "com.sun.activation", module = "javax.activation")
}


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("org.jetbrains.kotlin.plugin.serialization") version libs.versions.kotlin.get()

    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.anyaitalked.everytalk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anyaitalked.everytalk"
        minSdk = 27

        targetSdk = 35
        versionCode = 5946
        versionName = "1.25.0506"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isProfileable = false
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        create("benchmark1") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
    packaging{
        resources{
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/LICENSE-LGPL-3.txt"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts+="META-INF/LICENSE-LGPL-2.1.txt"
            pickFirsts+="META-INF/LICENSE-W3C-TEST"
        }
    }
}


    dependencies {

        implementation(platform("androidx.compose:compose-bom:2025.05.00"))
        androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))

        implementation(platform("androidx.compose:compose-bom:2025.05.00"))
        androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))


        implementation(libs.androidx.ui)
        implementation(libs.androidx.ui.graphics)
        implementation(libs.androidx.ui.tooling.preview)
        implementation(libs.androidx.material3)

        implementation("androidx.compose.material:material-icons-core")
        implementation("androidx.compose.material:material-icons-extended")
        implementation("androidx.compose.foundation:foundation")
        implementation("androidx.compose.animation:animation")

        debugImplementation("androidx.compose.ui:ui-tooling")


        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
        implementation("androidx.activity:activity-compose:1.10.1")


        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)


        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


        implementation("io.ktor:ktor-client-core:2.3.11")
        implementation("io.ktor:ktor-client-android:2.3.11")
        implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
        implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
        implementation("io.ktor:ktor-client-logging:2.3.11")
        implementation("io.ktor:ktor-client-cio:2.3.11")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.test.manifest)

        implementation("androidx.navigation:navigation-compose:2.7.7")
        implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
        implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")

        implementation(libs.androidx.profileinstaller)
        implementation ("org.slf4j:slf4j-nop:2.0.12")

        implementation("org.commonmark:commonmark:0.24.0")
        implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
        implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
        implementation("org.commonmark:commonmark-ext-autolink:0.24.0")

        implementation("org.jsoup:jsoup:1.17.2")

        implementation("io.coil-kt.coil3:coil-compose:3.2.0")
        implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")

        implementation ("com.google.accompanist:accompanist-flowlayout:0.30.1")
    }
