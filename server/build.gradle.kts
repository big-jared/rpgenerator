plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-server-cors:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")

    // Google Gemini SDK
    implementation("com.google.genai:google-genai:1.41.0")

    // Google ID token verification
    implementation("com.google.api-client:google-api-client:2.7.2")

    // Firebase Admin SDK (verify Firebase ID tokens from mobile app)
    implementation("com.google.firebase:firebase-admin:9.4.3")

    // SLF4J
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass.set("com.rpgenerator.server.MainKt")
}

// Build a fat JAR for Cloud Run deployment
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.rpgenerator.server.MainKt"
    }
    // Project classes FIRST so they aren't excluded by dependency duplicates
    with(tasks.jar.get() as CopySpec)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
