@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")

plugins {
    id("java")
    application
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

group = "br.com.vini"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    compileOnly("org.projectlombok:lombok:1.18.36")

    annotationProcessor("org.projectlombok:lombok:1.18.36")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew run     -> menu no terminal (br.com.vini.Main)
// ./gradlew runWeb  -> site + API em http://localhost:8080 (br.com.vini.web.WebServer)
application {
    mainClass.set("br.com.vini.Main")
}

tasks.register<JavaExec>("runWeb") {
    group = "application"
    description = "Sobe o site e a API web do JavaBank"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("br.com.vini.web.WebServer")
}

// nome fixo do jar (usado pelo Dockerfile)
tasks.jar {
    archiveFileName.set("javabank.jar")
}