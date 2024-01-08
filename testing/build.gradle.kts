dependencies {
    listOf(
            "org.apache.kafka:kafka-clients",
            "org.apache.kafka:kafka_2.13"
    ).forEach {
        implementation("$it:${project.extra["kafkaVersion"]}")
        implementation("$it:${project.extra["kafkaVersion"]}:test")
    }

    implementation("org.apache.zookeeper:zookeeper:3.5.6") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }
    implementation("org.junit.jupiter:junit-jupiter:${project.extra["junitVersion"]}")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.12")
}
