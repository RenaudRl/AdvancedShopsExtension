plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

val omniGuiVersion = "0.21"

// 0.21 is not on the GitHub releases yet: -PomniGuiJar=<path> compiles against a local build of it instead.
val omniGui: Any = providers.gradleProperty("omniGuiJar").orNull?.let { files(it) }
    ?: "btcrenaud:Typewriter-OmniGUIExtension:v$omniGuiVersion@jar"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external")
    maven("https://jitpack.io")
    ivy {
        name = "omniGuiGitHubReleases"
        url = uri("https://github.com/RenaudRl/Typewriter-OmniGUIExtension/releases/download")
        patternLayout {
            artifact("[revision]/Typewriter-OmniGUIExtension-$omniGuiVersion.[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    // Paper API comes from the typewriter { extension { paper() } } DSL below â€” the module-plugin
    // resolves it against engineVersion, same as every other extension (GUI, QuestCodex). A hardcoded
    // compileOnly("io.papermc.paper:paper-api:1.21.4-...") here was stale and redundant with that.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(omniGui)
    testImplementation(kotlin("test"))
    // The button type registry test loads the real OmniGUI classes, which compileOnly keeps off the test classpath.
    testImplementation(omniGui)
    // The artifact service builds its Gson when the class loads; the server provides it, tests do not.
    testRuntimeOnly("com.google.code.gson:gson:2.11.0")
}

group = "btc.renaud"
version = "1.13"

typewriter {
    namespace = "btcrenaud"
    extension {
        name = "Shops"
        shortDescription = "Advanced shop system for TypeWriter"
        description = """
            Shops is a TypeWriter extension providing a full-featured shop system.
            Features: dynamic and fixed pricing, stock management, per-player and global limits,
            item pools, taxes (global and per-item), full GUIExtension integration with customizable layouts,
            buy/sell toggles per item, configurable refresh rates, promotions, price trends,
            transaction history, categories, and persistent storage via artifact.
        """.trimIndent()
        engineVersion = "0.9.0-beta-177"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()

        dependencies {
            dependency(namespace = "renaud", name = "GuiAndDialogs")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

