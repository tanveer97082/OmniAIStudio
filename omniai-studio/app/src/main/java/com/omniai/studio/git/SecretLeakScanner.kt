package com.omniai.studio.git

import java.io.File

enum class SecretSeverity(val label: String) {
    CRITICAL("CRITICAL"),
    HIGH("HIGH"),
    MEDIUM("MEDIUM")
}

data class SecretLeakFinding(
    val filePath: String,
    val lineNumber: Int,
    val ruleName: String,
    val severity: SecretSeverity,
    val maskedSnippet: String
)

data class SecretScanReport(
    val scannedFileCount: Int = 0,
    val findings: List<SecretLeakFinding> = emptyList()
) {
    val hasCriticalLeaks: Boolean
        get() = findings.any { it.severity == SecretSeverity.CRITICAL || it.severity == SecretSeverity.HIGH }

    val isClean: Boolean
        get() = findings.isEmpty()
}

/**
 * v5.0 Pre-Push Secret Leak Scanner:
 * Pure-Kotlin regex detector that inspects staged project files prior to atomic GitHub pushes
 * to prevent accidental leakage of API tokens, JWTs, OAuth client secrets, or private keys.
 */
object SecretLeakScanner {

    private data class SecretRule(
        val name: String,
        val severity: SecretSeverity,
        val regex: Regex
    )

    private val rules: List<SecretRule> = listOf(
        SecretRule(
            name = "Private Key Material",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""-----BEGIN\s+(?:RSA|EC|OPENSSH|DSA|PGP)?\s*PRIVATE KEY-----""")
        ),
        SecretRule(
            name = "GitHub Personal Access Token",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\b(ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|gho_[A-Za-z0-9]{30,})\b""")
        ),
        SecretRule(
            name = "Google / Gemini API Key",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\bAIza[0-9A-Za-z\-_]{35}\b""")
        ),
        SecretRule(
            name = "OpenAI / Anthropic Secret Key",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\b(sk-(?:proj-|ant-api\d+-)?[A-Za-z0-9\-_]{24,})\b""")
        ),
        SecretRule(
            name = "Hugging Face Access Token",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\bhf_[A-Za-z0-9]{24,}\b""")
        ),
        SecretRule(
            name = "Groq API Key",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\bgsk_[A-Za-z0-9]{24,}\b""")
        ),
        SecretRule(
            name = "AWS Access Key ID",
            severity = SecretSeverity.CRITICAL,
            regex = Regex("""\bAKIA[0-9A-Z]{16}\b""")
        ),
        SecretRule(
            name = "Telegram Bot Token",
            severity = SecretSeverity.HIGH,
            regex = Regex("""\b\d{8,10}:[A-Za-z0-9_-]{35}\b""")
        ),
        SecretRule(
            name = "JWT / Supabase Service Role Token",
            severity = SecretSeverity.HIGH,
            regex = Regex("""\beyJhbGciOi[A-Za-z0-9_\-]{12,}\.[A-Za-z0-9_\-]{16,}\.[A-Za-z0-9_\-]{16,}\b""")
        ),
        SecretRule(
            name = "Hardcoded Secret Assignment",
            severity = SecretSeverity.MEDIUM,
            regex = Regex(
                """(?i)\b(?:api_key|client_secret|refresh_token|access_token|private_key|db_password|keystore_pass)\s*[:=]\s*["']([A-Za-z0-9_\-./+=]{16,})["']"""
            )
        )
    )

    private val placeholderHints = listOf(
        "YOUR_",
        "REPLACE_ME",
        "EXAMPLE",
        "DUMMY",
        "PLACEHOLDER",
        "CHANGEME",
        "<YOUR",
        "XXXX",
        "00000000"
    )

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "ico", "mp4", "mp3", "m4a", "wav",
        "zip", "apk", "jar", "jks", "keystore", "so", "ttf", "otf", "pdf"
    )

    fun scanFiles(files: Map<String, String>): SecretScanReport {
        val findings = mutableListOf<SecretLeakFinding>()
        var scannedCount = 0

        for ((path, content) in files) {
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in binaryExtensions) continue
            scannedCount++

            content.lineSequence().forEachIndexed { zeroIndex, line ->
                if (line.length > 4096) return@forEachIndexed
                val upperLine = line.uppercase()
                if (placeholderHints.any { upperLine.contains(it) }) return@forEachIndexed

                for (rule in rules) {
                    val match = rule.regex.find(line) ?: continue
                    val matchedValue = match.groupValues.lastOrNull { it.isNotBlank() } ?: match.value
                    findings += SecretLeakFinding(
                        filePath = path,
                        lineNumber = zeroIndex + 1,
                        ruleName = rule.name,
                        severity = rule.severity,
                        maskedSnippet = maskSecretValue(matchedValue)
                    )
                    break
                }
            }
        }

        return SecretScanReport(
            scannedFileCount = scannedCount,
            findings = findings
        )
    }

    fun scanDirectory(projectDir: File, relativePaths: List<String>? = null): SecretScanReport {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return SecretScanReport()
        }
        val canonicalRoot = projectDir.canonicalFile
        val filesToScan = linkedMapOf<String, String>()

        if (relativePaths != null) {
            relativePaths.forEach { rel ->
                val target = File(canonicalRoot, rel).canonicalFile
                if (target.path.startsWith(canonicalRoot.path + File.separator) &&
                    target.isFile &&
                    target.length() <= MAX_SCAN_FILE_BYTES
                ) {
                    runCatching { target.readText(Charsets.UTF_8) }.getOrNull()?.let { text ->
                        filesToScan[rel] = text
                    }
                }
            }
        } else {
            canonicalRoot.walkTopDown()
                .filter { it.isFile && it.length() <= MAX_SCAN_FILE_BYTES }
                .forEach { file ->
                    val rel = file.relativeTo(canonicalRoot).path.replace(File.separatorChar, '/')
                    runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.let { text ->
                        filesToScan[rel] = text
                    }
                }
        }

        return scanFiles(filesToScan)
    }

    fun maskSecretValue(raw: String): String {
        val clean = raw.trim()
        if (clean.length <= 8) return "••••••••"
        return "${clean.take(4)}••••${clean.takeLast(4)}"
    }

    private const val MAX_SCAN_FILE_BYTES = 1L * 1024L * 1024L
}
