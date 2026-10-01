import java.util.Properties

plugins {
    // AGP 9 부터 Kotlin 지원이 AGP에 내장되어 kotlin.android 플러그인을 적용하지 않는다.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Supabase 키는 저장소에 올리지 않는다. 내 PC 에서는 local.properties,
// GitHub 빌드에서는 환경 변수(Secrets)로 받는다. 값이 없으면 친구 기능이
// 비활성화되고 앱은 로컬 전용으로 동작한다.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** local.properties 를 먼저 보고, 없으면 환경 변수를 본다. */
fun secret(key: String, env: String): String =
    localProps.getProperty(key) ?: System.getenv(env) ?: ""

// 서명 키. 없으면 release 도 디버그 키로 서명한다. 친구에게 그냥 주기에는
// 그것으로 충분하고, 나중에 keystore 를 만들면 자동으로 그쪽을 쓴다.
val keystorePath = secret("keystore.path", "KEYSTORE_PATH")
val keystoreFile = keystorePath.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
val hasKeystore = keystoreFile?.exists() == true

android {
    namespace = "com.soc.scheduler"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.soc.scheduler"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("supabase.url", "SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("supabase.anonKey", "SUPABASE_ANON_KEY")}\"")
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("keystore.password", "KEYSTORE_PASSWORD")
                keyAlias = secret("keystore.alias", "KEYSTORE_ALIAS").ifBlank { "soc" }
                keyPassword = secret("keystore.keyPassword", "KEYSTORE_KEY_PASSWORD")
                    .ifBlank { secret("keystore.password", "KEYSTORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // keystore 가 있으면 그것으로, 없으면 디버그 키로 서명해 설치는 되게 한다.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // Kotlin jvmTarget 은 targetCompatibility 를 따라간다.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 아이콘은 core 세트만 사용한다 (extended 는 용량이 크고 사실상 동결 상태).
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.10.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
