plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jex.maclauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jex.maclauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
    }

    // 배포 서명: 키와 비밀번호는 ~/.gradle/gradle.properties 에만 (저장소에 넣지 않음)
    val storeFilePath = providers.gradleProperty("MACLAUNCHER_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("MACLAUNCHER_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("MACLAUNCHER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("MACLAUNCHER_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // 배터리 위젯: 블루투스 기기 배터리(BluetoothDevice.getBatteryLevel, 숨은 API) 읽기
    implementation(libs.hiddenapibypass)
    // 프리미엄 감성 팩 인앱 결제
    implementation(libs.billing.ktx)
    implementation(libs.androidx.fragment)  // billing이 끌어오는 옛 fragment를 올림 (ActivityResult lint)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
