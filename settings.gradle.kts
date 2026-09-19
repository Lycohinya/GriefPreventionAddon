rootProject.name = "GriefPreventionAddon"

// LycoLib 是獨立 repo/獨立 Gradle root project,用 composite build 接入以取得編譯期型別存取,
// 不需要發布到 Maven 倉庫(照 LycoQuest 的做法,見 docs/LYCOLIB_PLAN.md)。
includeBuild("../LycoLib")
