plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
    application
}

group = "com.mychatapp"
version = "1.0.0"

application {
    mainClass.set("com.mychatapp.ApplicationKt")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("mychatapp-server")
        imageTag.set("latest")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-server-cors:3.0.3")
    implementation("io.ktor:ktor-server-auth:3.0.3")
    implementation("io.ktor:ktor-server-auth-jwt:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("io.ktor:ktor-server-call-logging:3.0.3")
    implementation("io.ktor:ktor-server-rate-limit:3.0.3")
    
    // Ktor Client (for OpenRouter)
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    
    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.4.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.13")
    
    // Dotenv for local development
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.2")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JavaExec>("setAdmin") {
    group = "application"
    description = "Set admin claim for a user"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mychatapp.SetAdminKt")
}
