tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"))
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("io.netty:netty-all:4.1.108.Final")
    testImplementation("org.apache.kafka:kafka-clients:${project.extra["kafkaVersion"]}")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.12")
}
