dependencies {
    implementation("com.linecorp.armeria:armeria:1.26.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.12")

    itImplementation("org.apache.kafka:kafka-clients:${project.extra["kafkaVersion"]}")
    itImplementation("tools.profiler:async-profiler:2.9")
    itImplementation(project(":testing"))
    // For ordinary use, adding client-dependency as runtimeOnly is enough but
    // we add itImplementation here to make it visible for integration tests
    // to call `setTunnelingCondition`
    itImplementation(project(":client"))
}
