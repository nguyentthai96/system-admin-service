plugins {
    id("ntt.spring-app-conventions")
    alias(libs.plugins.kotlin.jpa)
}

group = "com.ntt"
version = "0.0.1-SNAPSHOT"

dependencies {
//  - BASE-CORE STARTERS (provides base-core, base-model, common-log transitively)
    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
    implementation("com.ntt:base-web-starter")
    implementation("com.ntt:base-data-starter")
    implementation("com.ntt:common-log")
//  - MAIN
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
//  - DEVELOPMENT
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
//  - TESTING
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.ntt:base-testing-starter")
    testRuntimeOnly("com.h2database:h2")
}

// Disable GraalVM AOT processing — runs as standard JVM app.
// AOT processAot conflicts with BaseEntity dual-@Id inheritance in base-core.
tasks.named("processAot") { enabled = false }
tasks.named("processTestAot") { enabled = false }
