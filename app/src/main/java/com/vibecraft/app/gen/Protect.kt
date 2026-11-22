package com.vibecraft.app.gen

/** Files Vibecraft manages itself, and the rules for what the AI may change or delete. */
object Protect {
    private val fixed = setOf(
        "settings.gradle.kts", "build.gradle.kts", "gradle.properties", ".gitignore",
        "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
        "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml",
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", "${CodeBlocks.GEN}/MainActivity.kt", "${CodeBlocks.GEN}/DesignTokens.kt",
    )

    fun isProtected(path: String) = path in fixed || path.startsWith(".github/") || path.startsWith("gradle/")

    /** The AI's files live in the sketch, the Kotlin sources and the resources. */
    fun canWrite(path: String): Boolean {
        if (isProtected(path)) return false
        return path == "preview.html" || path == "design.json" || path.startsWith("preview/") ||
            path.startsWith("app/src/main/java/") || path.startsWith("app/src/main/res/")
    }

    /** Files that still use something the file declares, so deleting it would break the build. */
    fun deleteBlockers(path: String, files: Map<String, String>): List<String> =
        Manifest.build(files).filter { path in it.deps }.map { it.path }

    fun canDelete(path: String) = canWrite(path) && !path.endsWith("/AppRoot.kt")
}
