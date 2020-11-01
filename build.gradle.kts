plugins {
    kotlin("jvm") version "1.4.10"
}

group = "dev.xdark"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("io.netty:netty-all:4.1.53.Final")
}

tasks {
    withType<Jar> {
        // Otherwise you'll get a "No main manifest attribute" error
        manifest {
            attributes["Agent-Class"] = "dev.xdark.nettyctl.NettyctlKt"
        }
    }
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    register<Jar>("fatJar") {
        baseName = "${project.name}-fat"
        from(
            configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
            exclude("META-INF/")
        }

        with(get("jar") as CopySpec)
    }
}