import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "br.com.hugolumazzini.havaltrip"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.hugolumazzini.havaltrip"
        // 24 e não 23 porque a biblioteca do Shizuku exige. Não custa nada: a
        // central do H6 roda Android bem acima disso.
        minSdk = 24
        targetSdk = 34
        // O `versionName` é o que a pessoa lê; o `versionCode` é o que decide
        // se a loja oferece atualização, e por isso só pode subir. Os dois
        // andam juntos de propósito — o code acompanha a minor — porque já
        // aconteceu de o release ser `v0.3.0` com o APK dizendo `1.0.0` por
        // dentro, e aí o catálogo da loja não tinha como comparar nada.
        //
        // A numeração é 0.x: a `v1.0.0` de 31/08/2026 foi a primeira tentativa,
        // e a contagem recomeçou em 0.2.0 no dia seguinte.
        versionCode = 5
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinatura de release fica de fora até existir uma chave própria
            // deste app: reaproveitar a chave da loja misturaria as identidades.
        }
    }

    buildFeatures {
        compose = true
        // As duas interfaces do serviço de veículo da GWM. Não são código
        // nosso: descrevem os comandos que a central já expõe, e existem aqui
        // porque o Android precisa gerar o intermediário para chamá-los.
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Todo o cálculo mora no :core, que não conhece Android. A camada de app só
    // desenha o que ele publica.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
