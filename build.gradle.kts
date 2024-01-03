import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

allprojects {
    group = "com.mayreh.kafka-tunnel"
    version = "$version" + if ((property("snapshot") as String).toBoolean()) "-SNAPSHOT" else ""
    extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")
}

subprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.30")
        "annotationProcessor"("org.projectlombok:lombok:1.18.30")
        "testCompileOnly"("org.projectlombok:lombok:1.18.30")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.30")

        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
        "testImplementation"("org.mockito:mockito-core:5.8.0")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.8.0")
    }

    tasks {
        named<Test>("test") {
            useJUnitPlatform()

            testLogging {
                events(TestLogEvent.FAILED,
                        TestLogEvent.PASSED,
                        TestLogEvent.SKIPPED,
                        TestLogEvent.STANDARD_OUT)
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true
                showStandardStreams = true
            }
        }
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                url = if (project.extra["isReleaseVersion"] as Boolean) {
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                } else {
                    uri("https://oss.sonatype.org/content/repositories/snapshots/")
                }
                credentials {
                    username = findProperty("sonatypeUsername").toString()
                    password = findProperty("sonatypePassword").toString()
                }
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name
                pom {
                    name = "Kafka Tunnel"
                    description = "Kafka-protocol tunnel over HTTP for Java"
                    url = "https://github.com/ocadaruma/kafka-tunnel"

                    scm {
                        connection = "scm:git:git@github.com:ocadaruma/kafka-tunnel.git"
                        url = "git@github.com:ocadaruma/kafka-tunnel.git"
                    }
                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "https://raw.githubusercontent.com/ocadaruma/kafka-tunnel/master/LICENSE"
                        }
                    }
                    developers {
                        developer {
                            id = "ocadaruma"
                            name = "Haruki Okada"
                        }
                    }
                }
            }
        }
    }

    configure<SigningExtension> {
        isRequired = (project.extra["isReleaseVersion"] as Boolean) && gradle.taskGraph.hasTask("publish")
        sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
    }
}
