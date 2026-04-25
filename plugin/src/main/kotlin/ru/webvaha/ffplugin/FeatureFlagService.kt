package ru.webvaha.ffplugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Project-level service. Читает feature-flags.json из корня проекта.
 * Кеширует результат и перечитывает файл только если он изменился
 * (сравнивает lastModified), чтобы не бить по диску на каждое нажатие клавиши.
 */
@Service(Service.Level.PROJECT)
class FeatureFlagService(private val project: Project) {

    @Volatile private var cachedFlags: Map<String, Boolean>? = null
    @Volatile private var lastModified: Long = -1

    fun getFlags(): Map<String, Boolean>? {
        val basePath = project.basePath ?: return null
        val file = File(basePath, "feature-flags.json")
        if (!file.exists()) return null

        val modified = file.lastModified()
        if (modified != lastModified) {
            synchronized(this) {
                // double-checked locking
                if (modified != lastModified) {
                    cachedFlags = try {
                        parseFlags(file.readText())
                    } catch (_: Exception) {
                        null
                    }
                    lastModified = modified
                }
            }
        }

        return cachedFlags
    }

    /**
     * Минималистичный парсер JSON объекта вида {"key": true/false, ...}.
     * Не тянет лишних зависимостей — IntelliJ Platform уже включает Gson,
     * но лучше не полагаться на детали реализации платформы.
     */
    private fun parseFlags(json: String): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        val pattern = Regex(""""([^"]+)"\s*:\s*(true|false)""")
        for (match in pattern.findAll(json)) {
            result[match.groupValues[1]] = match.groupValues[2] == "true"
        }
        return result
    }
}
