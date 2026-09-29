plugins {
    base
    id("com.gradleup.shadow") version "9.3.1" apply false
}

allprojects {
    group = "fr.nayz.hydrogen"
    version = "0.7.3"
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
