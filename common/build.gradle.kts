plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

dependencies {
    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("io.lettuce:lettuce-core:6.8.1.RELEASE")
    implementation("org.postgresql:postgresql:42.7.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("migrateDatabase") {
    group = "database"
    description = "Apply Hydrogen PostgreSQL migrations once before starting plugins"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("fr.nayz.hydrogen.core.database.MigrationRunner")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "common"
        }
    }
}
