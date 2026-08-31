import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    application
}

// O núcleo é Kotlin/JVM puro, sem uma linha de Android. Isso é de propósito:
// os cálculos de trip e a máquina de estados rodam nos testes e no demo do
// terminal sem emulador, e a central só consome o resultado.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Bytecode 17 para casar com o módulo Android, sem exigir um JDK 17 instalado:
// o JDK do Android Studio (mais novo) compila mirando a versão 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("br.com.hugolumazzini.havaltrip.demo.DemoKt")
}

// ./gradlew demo  →  roda a simulação de condução no terminal.
tasks.register("demo") {
    group = "application"
    description = "Executa a simulação de condução passo a passo no terminal."
    dependsOn(tasks.named("run"))
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.withType<Test> { useJUnit() }
