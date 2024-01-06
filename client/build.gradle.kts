tasks.withType<JavaCompile>().configureEach {
//    println(layout.buildDirectory.get())
//    println(files(sourceSets.main.get().java.srcDirs).asPath)
//    options.compilerArgs.addAll(
//            arrayOf("--patch-module", "java.base=${files(sourceSets.main.get().java.srcDirs).asPath}"))
}

//tasks.withType<Test>().configureEach {
//    jvmArgs("-Djava.nio.channels.spi.SelectorProvider=com.mayreh.kafka.http.tunnel.client.TunnelingSelectorProvider")
//}

//testing {
//    configure<JavaPluginExtension> {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//}

dependencies {
    "implementation"("org.slf4j:slf4j-api:1.7.30")
    "testImplementation"("org.apache.kafka:kafka-clients:3.6.0")
    "runtimeOnly"("ch.qos.logback:logback-classic:1.4.11")
}
