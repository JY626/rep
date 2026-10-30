import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * 发布签名：仓库根目录存在 `keystore.properties`（不进 git）时才启用 release 签名，
 * 否则回退 debug 签名，保证 clone 下来就能编、能装。
 *
 * keystore.properties 内容：
 *   storeFile=rep-release.jks
 *   storePassword=***
 *   keyAlias=rep
 *   keyPassword=***
 *
 * 注意：debug 签名只能自用。一旦对外分发，后续版本换了签名就无法覆盖安装，
 * 用户只能卸载重装（数据会丢，除非先导出 JSON 备份）。
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it) }
val hasReleaseKeystore = releaseStoreFile?.exists() == true

android {
    namespace = "com.baoji.fitness"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baoji.fitness"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 暂不开 R8：Health Connect 记录类与 Room 生成代码需要真机跑通才能确认规则够用，
            // 未验证就开混淆等于赌运行时崩溃。要开请先补 keep 规则并跑一遍 connectedAndroidTest。
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // 没有 keystore 时的兜底：仅适合本机自用安装
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // 迁移测试需要读取 app/schemas/ 下导出的 JSON schema
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    // 导出 Room schema，迁移测试与后续版本 diff 都依赖它
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager（训练日提醒）
    implementation(libs.androidx.work.runtime.ktx)

    // Health Connect（同步训练与身体数据给 AI 教练）
    implementation(libs.androidx.health.connect)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
