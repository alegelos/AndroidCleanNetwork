plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.alegelos"
version = "1.0.0"

android {
    namespace = "com.alegelos.androidcleannetwork"
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
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "android-clean-network"
                version = project.version.toString()
                pom {
                    name.set("AndroidCleanNetwork")
                    description.set("Clean Architecture networking contracts and an OkHttp transport for Android.")
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
