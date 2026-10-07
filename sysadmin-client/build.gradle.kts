plugins {
    id("java-library")
    kotlin("jvm")
    kotlin("plugin.jpa")
    `maven-publish`
}

group = "com.ntt"
version = "0.0.1-SNAPSHOT"

dependencies {
    api(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
    api("com.ntt:base-data-starter")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Caffeine L1 cache for sub-millisecond reference data reads
    api("com.github.ben-manes.caffeine:caffeine")

    // Compile-only: base-business contracts (ISP interfaces + VOs)
    api("com.ntt:base-business:0.0.1-SNAPSHOT")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "sysadmin-client"
        }
    }
}
