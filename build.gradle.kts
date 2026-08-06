plugins {
    id("ntt.spring-app-conventions")
}

group = "com.ntt"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(platform("com.ntt:platform:0.0.1-SNAPSHOT"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Internal base core dependencies (uncomment after publishing base-core to mavenLocal)
    // implementation("com.ntt:base-web-starter")
    // implementation("com.ntt:base-data-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
