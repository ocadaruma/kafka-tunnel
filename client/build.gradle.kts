dependencies {
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("com.github.marianobarrios:tls-channel:0.8.1")
    testImplementation("org.apache.kafka:kafka-clients:${project.extra["kafkaVersion"]}")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.12")
}
