plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vexor.vault"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vexor.vault"
        minSdk = 26
        targetSdk = 34
        versionCode = 410
        versionName = "4.1.0"
        multiDexEnabled = true
        setProperty("archivesBaseName", "Vexor-v4.1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            pickFirsts += "lib/**/libimage_processing_util_jni.so"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Security (encrypted shared prefs, crypto)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Room Database (encrypted)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Image loading
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")
    
    // Media
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    
    // Camera (for intruder detection)
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    // implementation("androidx.camera:camera-view:1.3.1") // Removed as problematic
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.objecthunter:exp4j:0.4.8")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    
    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
