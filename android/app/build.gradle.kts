import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.namunkiro"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.namunkiro"
        minSdk = 28
        // Pleos 샘플 기준 35. 에뮬레이터는 API 34(Pleos Connect v2.1.0)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // release 서명: android/keystore.properties(저장소 제외)가 있을 때만 적용. 키스토어 파일도 저장소 밖에 둔다.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DEMO_PANEL", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // 심사관이 APK만으로 인물 전환·모의/실제 차량 전환을 해 볼 수 있도록 시연 패널 포함(⚙ → 시연 설정)
            buildConfigField("boolean", "DEMO_PANEL", "true")
            // R8 최적화 끔: packageScope 부분 최적화가 kotlin.collections 패키지 전용 상속을 깨서
            // 시작 즉시 IllegalAccessError가 났다(docs/rehearsal/final/RELEASE_CHECK.md). 심사 APK는 안정성 우선.
            optimization {
                enable = false
            }
        }
        // PT·영상용: 디버그 서명, 디버깅 불가, 데모 패널 포함
        create("demo") {
            initWith(getByName("debug"))
            isDebuggable = false
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "DEMO_PANEL", "true")
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
    // AAOS 표준 API(CarUxRestrictionsManager: 주행 규제)
    useLibrary("android.car")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
