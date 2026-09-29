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
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("fr.nayz.endercloud:paper:0.1.0")
    implementation("fr.mrmicky:fastboard:2.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("fr.nayz.endercloud:paper:0.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("HydrogenPaper")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "fr.nayz.hydrogen.libs.hikari")
    relocate("io.lettuce", "fr.nayz.hydrogen.libs.lettuce")
    relocate("io.netty", "fr.nayz.hydrogen.libs.netty")
    relocate("org.postgresql", "fr.nayz.hydrogen.libs.postgresql")
    relocate("fr.mrmicky.fastboard", "fr.nayz.hydrogen.libs.fastboard")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["shadow"])
            artifactId = "paper-api"
        }
    }
}
