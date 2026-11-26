plugins {
    java
}

group = "ru.outlands"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.nexomc.com/releases/") {
        content { includeGroup("com.nexomc") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Nexo's published API. Only NexoItems and ItemBuilder are used, so its own dependencies are not needed to
    // compile; compileOnly keeps it out of the plugin jar.
    compileOnly("com.nexomc:nexo:1.30.2") {
        isTransitive = false
    }

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showExceptions = true
    }
}
