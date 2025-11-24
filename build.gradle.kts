plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("com.typewritermc.module-plugin")
}

group = "com.btc.shops"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.insert-koin:koin-core:3.5.6")
    testImplementation(kotlin("test"))
}

typewriter {
    namespace = "borntocraft"
    extension {
        name = "Shops"
        shortDescription = "UltimateShop-like extension"
        description = """
            Shops is a TypeWriter extension that provides an UltimateShop-like shopping system. It demonstrates how
            dynamic pricing, stock handling and simple GUI services could be implemented using Kotlin. The extension
            included here is intentionally lightweight and meant for demonstration and testing purposes within the
            BornToCraft environment.
        """.trimIndent()
        engineVersion = file("../../version.txt").readText().trim()
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        dependencies {
            paper()
        }
    }
}

kotlin {
    jvmToolchain(21)
}


