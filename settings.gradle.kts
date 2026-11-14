@file:Suppress("UnstableApiUsage")

import java.io.File
import java.util.zip.ZipFile

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// Clean up any leftover template java sources or old multi-module directories
File(rootDir, "app/src/main/java").deleteRecursively()
File(rootDir, "app/src/androidTest").deleteRecursively()
File(rootDir, "app/src/test/java/com/example").deleteRecursively()
File(rootDir, "providers").deleteRecursively()
File(rootDir, "modules").deleteRecursively()

// Self-healing single-module extractor: extracts :app, all 16 :providers:*, and all 5 :modules:*
// into :app so all 22 modules compile on a single Gradle daemon without multi-module overhead.
val archiveFile = File(rootDir, "public/assets/aistudio/Mochy-Music-main_dc97d0a7.zip")
val markerFile = File(rootDir, "app/src/main/kotlin/_providers/.extracted_v2")
if (archiveFile.exists() && !markerFile.exists()) {
    val skipFiles = setOf(
        "app/src/main/res/values/app_name.xml",
        "app/src/main/res/values/themes.xml",
        "app/src/main/res/drawable/ic_launcher_background.xml"
    )
    val alwaysOverwriteFromZip = setOf(
        "app/src/main/res/values/strings.xml",
        "app/src/main/res/values/styles.xml",
        "app/src/main/res/values/colors.xml"
    )
    ZipFile(archiveFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val rel = entry.name.substringAfter('/', "")
            if (rel.isEmpty() || rel in skipFiles) continue

            // Skip translated values-<locale> folders so aapt2 only links default values/ and values-night/
            if (rel.startsWith("app/src/main/res/values-") && !rel.startsWith("app/src/main/res/values-night/")) {
                continue
            }

            val targetRel = when {
                rel.startsWith("app/src/foss/kotlin/") ->
                    "app/src/main/kotlin/" + rel.removePrefix("app/src/foss/kotlin/")
                rel.startsWith("providers/") && ("/src/main/kotlin/" in rel || "/src/main/java/" in rel) && rel.endsWith(".kt") -> {
                    val modName = rel.removePrefix("providers/").substringBefore('/')
                    val subPath = rel.substringAfter("/src/main/").removePrefix("kotlin/").removePrefix("java/")
                    val dirPart = subPath.substringBeforeLast('/', "")
                    val filePart = subPath.substringAfterLast('/')
                    val safeFile = "${modName}_${filePart}"
                    if (dirPart.isEmpty()) "app/src/main/kotlin/_providers/$modName/$safeFile"
                    else "app/src/main/kotlin/_providers/$modName/$dirPart/$safeFile"
                }
                rel.startsWith("modules/") && ("/src/main/kotlin/" in rel || "/src/main/java/" in rel) && rel.endsWith(".kt") -> {
                    val modName = rel.removePrefix("modules/").substringBefore('/')
                    val subPath = rel.substringAfter("/src/main/").removePrefix("kotlin/").removePrefix("java/")
                    val dirPart = subPath.substringBeforeLast('/', "")
                    val filePart = subPath.substringAfterLast('/')
                    val safeFile = "mod_${modName}_${filePart}"
                    if (dirPart.isEmpty()) "app/src/main/kotlin/_modules/$modName/$safeFile"
                    else "app/src/main/kotlin/_modules/$modName/$dirPart/$safeFile"
                }
                rel.startsWith("app/src/main/") ||
                    rel.startsWith("app/schemas/") ||
                    rel == "compose_compiler_config.conf" ||
                    rel == "lint.xml" ||
                    rel == "app/lint.xml" ||
                    rel == "app/proguard-rules.pro" ||
                    rel == "app/proguard-profileable.pro" -> rel
                else -> null
            } ?: continue

            val outFile = File(rootDir, targetRel)
            if (!outFile.exists() || targetRel in alwaysOverwriteFromZip) {
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    // Remove any previously extracted values-<locale> directories (keep values and values-night)
    File(rootDir, "app/src/main/res").listFiles()?.filter {
        it.isDirectory && it.name.startsWith("values-") && it.name != "values-night"
    }?.forEach { it.deleteRecursively() }

    // Patch strings.xml to ensure app_name is "Mochy Music" and all default strings exist
    val stringsFile = File(rootDir, "app/src/main/res/values/strings.xml")
    if (stringsFile.exists()) {
        var content = stringsFile.readText()
        content = content.replace(
            Regex("""<string name="app_name">[^<]*</string>"""),
            """<string name="app_name">ASL Beats</string>"""
        )
        val extraDefaults = mapOf(
            "bedside_clock_caption" to "Full-screen clock and cover while charging",
            "bedside_face_analog" to "Analog",
            "bedside_face_analog_caption" to "Classic dial face",
            "bedside_face_big" to "Big Digital",
            "bedside_face_big_caption" to "Large bold time display",
            "bedside_face_cover" to "Album Cover",
            "bedside_face_cover_caption" to "Artwork backdrop with clock",
            "bedside_face_stacked" to "Stacked",
            "bedside_face_stacked_caption" to "Hours over minutes",
            "module_by" to "by %1\$s",
            "module_test_body" to "Testing source module connection",
            "module_test_body_drm" to "Testing source module DRM stream",
            "module_testing" to "Testing module…",
            "module_use" to "Use module",
            "modules_find_more_desc" to "Browse and install additional audio source modules",
            "modules_intro" to "Extend ASL Beats with external audio source modules"
        )
        val missingXml = buildString {
            for ((k, v) in extraDefaults) {
                if (!content.contains("""name="$k"""")) {
                    append("    <string name=\"$k\">$v</string>\n")
                }
            }
        }
        if (missingXml.isNotEmpty()) {
            content = content.replace("</resources>", "$missingXml</resources>")
        }
        stringsFile.writeText(content)
    }

    File(rootDir, "app/src/main/res").listFiles()?.filter { it.name.startsWith("mipmap-") }?.forEach { dir ->
        dir.listFiles()?.filter { it.extension == "webp" }?.forEach { webp ->
            if (File(dir, webp.nameWithoutExtension + ".png").exists()) {
                webp.delete()
            }
        }
    }
    markerFile.parentFile?.mkdirs()
    markerFile.writeText("ok")
}

rootProject.name = "ASL Beats"
include(":app")
