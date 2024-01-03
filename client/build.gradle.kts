tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(arrayOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"))
}

dependencies {
//    "implementation"("org.apache.kafka:kafka-clients:3.6.0")
//    "implementation"("io.netty:netty-all:4.1.101.Final")
}
