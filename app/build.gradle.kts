plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace   = "com.clink.app"
    compileSdk  = 36

    defaultConfig {
        applicationId         = "com.clink.app"
        minSdk                = 26
        targetSdk             = 36
        versionCode           = 1
        versionName           = "0.1.0"

        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            // R8 全量优化 + 资源压缩
            isMinifyEnabled       = true
            isShrinkResources     = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    // 体积压缩配置
    bundle {
        // AAB 按需分发：ABI、density、language 均拆分
        abi        { enableSplit = true  }
        density    { enableSplit = true  }
        language   { enableSplit = true  }
    }

    packaging {
        resources {
            // 剔除不必要的元数据文件
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "META-INF/MANIFEST.MF",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
            )
        }
    }

    // 禁用 PNG 压缩（仅用 VectorDrawable，无 PNG，节省工具耗时）
    androidResources {
        noCompress += listOf("webp")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // 开启内联优化
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xjvm-default=all"
        )
    }

    // 不使用 ViewBinding 来降低编译体积，手动 findViewById
    buildFeatures {
        viewBinding    = false
        buildConfig    = true   // 仅在需要 BuildConfig 常量时开启
        resValues      = false
        aidl           = false
        renderScript   = false
        shaders        = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.appcompat)

    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.runtime)

    implementation(libs.coroutines.android)
}
