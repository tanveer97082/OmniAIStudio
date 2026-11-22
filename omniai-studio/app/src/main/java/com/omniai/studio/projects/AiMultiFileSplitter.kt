package com.omniai.studio.projects

import java.io.File

data class ParsedProjectFile(
    val relativePath: String,
    val content: String,
    val languageHint: String = ""
)

data class SplitWriteResult(
    val writtenPaths: List<String>,
    val skippedPaths: List<String>
)

/**
 * Parses AI multi-file responses formatted with `### FILE: relative/path.ext` headers
 * (as well as fenced code blocks from Chat Studio) and writes them safely inside the
 * confined project workspace `filesDir/projects/{slug}/`.
 */
object AiMultiFileSplitter {

    private val FILE_HEADER_REGEX = Regex(
        """^\s*(?:#{1,6}\s*)?(?:FILE|File|PATH|Path)\s*:\s*`?([^`\r\n]+?)`?\s*$"""
    )

    private val FENCE_START_REGEX = Regex("""^\s*```([a-zA-Z0-9_+#.-]*)\s*$""")
    private val FENCE_END_REGEX = Regex("""^\s*```\s*$""")

    fun hasSaveableCodeOrFiles(rawText: String): Boolean {
        if (rawText.isBlank()) return false
        if (rawText.lines().any { FILE_HEADER_REGEX.matches(it) }) return true
        return rawText.contains("```")
    }

    /**
     * Extracts all `### FILE: ...` sections from an AI response.
     * If no explicit `### FILE:` headers exist, falls back to extracting fenced code blocks
     * with sensible inferred filenames (e.g. `snippet_1.kt`, `snippet_2.py`, `index.html`).
     */
    fun parseMultiFileOutput(rawText: String): List<ParsedProjectFile> {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val explicitFiles = parseExplicitFileHeaders(lines)
        if (explicitFiles.isNotEmpty()) return explicitFiles
        return parseFallbackCodeBlocks(lines)
    }

    private fun parseExplicitFileHeaders(lines: List<String>): List<ParsedProjectFile> {
        val results = mutableListOf<ParsedProjectFile>()
        var currentPath: String? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            val rawPath = currentPath ?: return
            val sanitized = sanitizeRelativePath(rawPath)
            if (sanitized != null) {
                val unwrapped = unwrapOuterCodeFence(buffer)
                results += ParsedProjectFile(
                    relativePath = sanitized,
                    content = unwrapped.first,
                    languageHint = unwrapped.second
                )
            }
            buffer.clear()
        }

        var insideFence = false
        for (line in lines) {
            if (FENCE_START_REGEX.matches(line) && !insideFence) {
                insideFence = true
                if (currentPath != null) buffer += line
                continue
            }
            if (FENCE_END_REGEX.matches(line) && insideFence) {
                insideFence = false
                if (currentPath != null) buffer += line
                continue
            }
            val headerMatch = if (!insideFence) FILE_HEADER_REGEX.matchEntire(line) else null
            if (headerMatch != null) {
                flush()
                currentPath = headerMatch.groupValues[1].trim()
            } else if (currentPath != null) {
                buffer += line
            }
        }
        flush()

        // Deduplicate by path keeping the latest block
        val byPath = linkedMapOf<String, ParsedProjectFile>()
        results.forEach { byPath[it.relativePath] = it }
        return byPath.values.toList()
    }

    private fun parseFallbackCodeBlocks(lines: List<String>): List<ParsedProjectFile> {
        val results = mutableListOf<ParsedProjectFile>()
        var insideFence = false
        var lang = ""
        val buf = mutableListOf<String>()
        var counter = 1

        for (line in lines) {
            if (!insideFence) {
                val startMatch = FENCE_START_REGEX.matchEntire(line)
                if (startMatch != null) {
                    insideFence = true
                    lang = startMatch.groupValues[1].trim().lowercase()
                    buf.clear()
                }
            } else {
                if (FENCE_END_REGEX.matches(line)) {
                    insideFence = false
                    val body = buf.joinToString("\n").trimEnd()
                    if (body.isNotBlank()) {
                        val inferredPath = inferPathFromBlock(body, lang, counter++)
                        results += ParsedProjectFile(
                            relativePath = inferredPath,
                            content = body + "\n",
                            languageHint = lang
                        )
                    }
                    buf.clear()
                } else {
                    buf += line
                }
            }
        }
        return results
    }

    private fun unwrapOuterCodeFence(rawLines: List<String>): Pair<String, String> {
        val trimmed = rawLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (trimmed.isEmpty()) return "" to ""
        val firstMatch = FENCE_START_REGEX.matchEntire(trimmed.first())
        val lastIsFence = trimmed.size >= 2 && FENCE_END_REGEX.matches(trimmed.last())
        return if (firstMatch != null && lastIsFence) {
            val body = trimmed.subList(1, trimmed.size - 1).joinToString("\n")
            (if (body.isEmpty()) "" else "$body\n") to firstMatch.groupValues[1].lowercase()
        } else {
            (trimmed.joinToString("\n") + "\n") to ""
        }
    }

    private fun inferPathFromBlock(body: String, lang: String, index: Int): String {
        val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
        val commentPath = Regex("""^(?://|#|<!--|/\*)\s*(?:file|path)\s*:\s*([^\s*]+)""" , RegexOption.IGNORE_CASE)
            .find(firstLine)?.groupValues?.getOrNull(1)
        val sanitizedComment = commentPath?.let { sanitizeRelativePath(it) }
        if (sanitizedComment != null) return sanitizedComment

        val ext = when (lang) {
            "kotlin", "kt" -> "kt"
            "kts", "gradle" -> "kts"
            "python", "py" -> "py"
            "typescript", "ts" -> "ts"
            "tsx" -> "tsx"
            "javascript", "js" -> "js"
            "jsx" -> "jsx"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yml"
            "sh", "bash", "shell" -> "sh"
            "sql" -> "sql"
            "md", "markdown" -> "md"
            else -> "txt"
        }
        return if (ext == "html" && index == 1) "index.html" else "ai_output/snippet_${index}.$ext"
    }

    /**
     * Normalizes a relative path and rejects traversal attempts (`..`), absolute paths,
     * null bytes, or `.git/` internal directory overwrites.
     */
    fun sanitizeRelativePath(rawPath: String): String? {
        val cleaned = rawPath
            .trim()
            .trim('"', '\'', '`')
            .replace('\\', '/')
            .trimStart('/')
        if (cleaned.isBlank() || cleaned.contains('\u0000')) return null
        val segments = cleaned.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.isEmpty()) return null
        if (segments.any { it == ".." || it.equals(".git", ignoreCase = true) }) return null
        return segments.joinToString("/")
    }

    /**
     * Resolves a relative path safely inside [projectRoot] with canonical prefix verification.
     */
    fun resolveConfinedFile(projectRoot: File, relativePath: String): File? {
        val sanitized = sanitizeRelativePath(relativePath) ?: return null
        if (!projectRoot.exists()) projectRoot.mkdirs()
        val rootCanonical = projectRoot.canonicalPath
        val candidate = File(projectRoot, sanitized)
        val candidateCanonical = candidate.canonicalPath
        if (!candidateCanonical.startsWith(rootCanonical + File.separator)) return null
        return candidate
    }

    /**
     * Writes all parsed files into [projectRoot] with strict path confinement.
     */
    fun writeParsedFilesToProject(
        projectRoot: File,
        files: List<ParsedProjectFile>
    ): SplitWriteResult {
        if (!projectRoot.exists()) projectRoot.mkdirs()
        val written = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (item in files) {
            val target = resolveConfinedFile(projectRoot, item.relativePath)
            if (target == null) {
                skipped += item.relativePath
                continue
            }
            target.parentFile?.mkdirs()
            target.writeText(item.content, Charsets.UTF_8)
            written += sanitizeRelativePath(item.relativePath) ?: item.relativePath
        }
        return SplitWriteResult(writtenPaths = written, skippedPaths = skipped)
    }

    fun slugifyProjectName(name: String): String {
        val normalized = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalized.ifBlank { "project-${System.currentTimeMillis() % 10000}" }.take(48)
    }
}
