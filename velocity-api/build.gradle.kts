plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:4.2.0")
    annotationProcessor("com.velocitypowered:velocity-api:4.2.0")
    testImplementation("com.velocitypowered:velocity-api:4.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveBaseName.set("HydrogenVelocity")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "fr.nayz.hydrogen.libs.hikari")
    relocate("io.lettuce", "fr.nayz.hydrogen.libs.lettuce")
    relocate("io.netty", "fr.nayz.hydrogen.libs.netty")
    relocate("org.postgresql", "fr.nayz.hydrogen.libs.postgresql")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["shadow"])
            artifactId = "velocity-api"
        }
    }
}
