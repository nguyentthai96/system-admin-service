plugins {
    id("ntt.spring-app-conventions")
    alias(libs.plugins.kotlin.jpa)
}

// group and version are inherited from gradle.properties (Single Source of Truth)

extra["springCloudVersion"] = libs.versions.spring.cloud.get()

dependencies {
    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
//  - BASE-CORE STARTERS
    implementation("com.ntt:base-web-starter")
    implementation("com.ntt:base-data-starter")
    implementation("com.ntt:common-log")
//  - MAIN
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("org.springframework.kafka:spring-kafka")
//  - DEVELOPMENT
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
//  - TESTING
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable GraalVM AOT processing
tasks.named("processAot") { enabled = false }
tasks.named("processTestAot") { enabled = false }
