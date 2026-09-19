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
    // GriefPrevention 僅供編譯期參照 API 型別,不 shade;執行期呼叫做 softdepend 存在檢查
    compileOnly(libs.griefprevention.api)
    // 選單引擎硬依賴(plugin.yml depend),不 shade,執行期由 Paper 從 plugins/ 載入 LycoLib 本體
    // (比照 LycoQuest 的做法——殼層背景、GuardedMenu 與 icon 目錄都靠這個型別存取)
    compileOnly("com.tinyyana:LycoLib:0.1.0")

    testImplementation(libs.paper.api)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(kotlin("test"))
    testImplementation(libs.griefprevention.api)
    testImplementation("com.tinyyana:LycoLib:0.1.0")
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
