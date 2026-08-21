plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
    // stdlib 不 shade: runtime 由 plugin.yml 的 libraries 提供
    compileOnly(libs.kotlin.stdlib)
    // LycoLib 由伺服器以獨立插件載入,不 shade
    compileOnly("com.tinyyana:LycoLib:0.1.0")
    // GriefPrevention 僅供編譯期參照 API 型別,不 shade;執行期呼叫做 softdepend 存在檢查
    compileOnly(libs.griefprevention.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(kotlin("test"))
    testImplementation("com.tinyyana:LycoLib:0.1.0")
    testImplementation(libs.griefprevention.api)
}

kotlin {
    jvmToolchain(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("thin")
    }

    shadowJar {
        archiveClassifier.set("")
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        useJUnitPlatform()
        inputs.file("src/main/resources/plugin.yml")
    }

    processResources {
        val props = mapOf("version" to version.toString(), "description" to project.description.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
