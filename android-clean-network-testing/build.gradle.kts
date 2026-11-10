plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.alegelos"
version = "1.0.1"

android {
    namespace = "com.alegelos.androidcleannetwork.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.all { it.useJUnit() }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":android-clean-network"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "android-clean-network-testing"
                version = project.version.toString()
                pom {
                    name.set("AndroidCleanNetwork Testing")
                    description.set("Fixture and provider-spy support for AndroidCleanNetwork.")
                    url.set("https://github.com/alegelos/AndroidCleanNetwork")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("alegelos")
                            name.set("Alejandro Gelos")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/alegelos/AndroidCleanNetwork.git")
                        developerConnection.set("scm:git:ssh://git@github.com/alegelos/AndroidCleanNetwork.git")
                        url.set("https://github.com/alegelos/AndroidCleanNetwork")
                    }
                }
            }
        }
    }
}
