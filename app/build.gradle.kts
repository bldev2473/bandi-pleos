import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Pleos Playground 프로젝트의 Client ID, Secret(TextToSpeech 서버 모드용)은 local.properties에 둔다. 저장소에 올리지 않는다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "kr.bandi.car"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.bandi.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProps.getProperty("pleos.clientId", "")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProps.getProperty("pleos.clientSecret", "")}\"")
        // 트래블젠 서버의 차량 전용 행사 질문 경로(/api/car/ask). 키가 없으면 앱 안 데이터로만 답한다.
        buildConfigField("String", "TRAVELGEN_API_BASE", "\"${localProps.getProperty("travelgen.apiBase", "https://travelgen.kr")}\"")
        buildConfigField("String", "TRAVELGEN_CAR_KEY", "\"${localProps.getProperty("travelgen.carKey", "")}\"")
    }

    // release 서명 키는 저장소 밖에 두고 local.properties로 가리킨다(bandi.storeFile 등). 없으면 서명하지 않은 release가 나온다.
    val releaseStore = localProps.getProperty("bandi.storeFile")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = localProps.getProperty("bandi.storePassword")
                keyAlias = localProps.getProperty("bandi.keyAlias")
                keyPassword = localProps.getProperty("bandi.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 코드 줄이기(R8)를 켜야 APK가 40MB대에서 크게 준다. Pleos SDK 보존 규칙은 proguard-rules.pro에 있다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // 배포용 APK에는 시험용 서버 주소와 차량 키를 넣지 않는다. 키는 공개 저장소의 APK에서 꺼낼 수 있기 때문이다.
            // 넣어야 할 때만 travelgen.apiBase.release, travelgen.carKey.release 로 따로 지정한다.
            buildConfigField("String", "TRAVELGEN_API_BASE", "\"${localProps.getProperty("travelgen.apiBase.release", "https://travelgen.kr")}\"")
            buildConfigField("String", "TRAVELGEN_CAR_KEY", "\"${localProps.getProperty("travelgen.carKey.release", "")}\"")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
