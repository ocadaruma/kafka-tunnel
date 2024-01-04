tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(arrayOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.nio.channels.spi.SelectorProvider=com.mayreh.kafka.http.tunnel.client.TunnelingSelectorProvider")
}

dependencies {
    "implementation"("org.slf4j:slf4j-api:1.7.30")
    "testImplementation"("org.apache.kafka:kafka-clients:3.6.0")
    "runtimeOnly"("ch.qos.logback:logback-classic:1.4.11")
}
