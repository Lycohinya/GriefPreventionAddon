rootProject.name = "GriefPreventionAddon"

// 每個 Lyco* 插件是獨立 Gradle root project,共用元件走 composite build
includeBuild("../LycoLib")
