package com.devlite.studio.model

/**
 * Supported languages and the metadata needed to locate an installed
 * toolchain and build the correct run command for a source file.
 */
enum class Language(
    val displayName: String,
    val extensions: List<String>,
    val toolchainId: String
) {
    PYTHON("Python", listOf("py"), "python"),
    JAVASCRIPT("JavaScript", listOf("js"), "node"),
    TYPESCRIPT("TypeScript", listOf("ts"), "node"),
    C("C", listOf("c"), "clang"),
    CPP("C++", listOf("cpp", "cc", "cxx"), "clang"),
    RUST("Rust", listOf("rs"), "rust"),
    GO("Go", listOf("go"), "go"),
    JAVA("Java", listOf("java"), "jdk"),
    KOTLIN("Kotlin", listOf("kt", "kts"), "kotlinc"),
    HTML("HTML", listOf("html", "htm"), "webview"),
    CSS("CSS", listOf("css"), "webview"),
    PLAIN_TEXT("Plain Text", emptyList(), "");

    companion object {
        fun fromExtension(ext: String): Language =
            entries.firstOrNull { ext.lowercase() in it.extensions } ?: PLAIN_TEXT
    }
}

/** A node in the file tree drawer, backed by a SAF document URI. */
sealed class FileNode {
    abstract val name: String
    abstract val uriString: String

    data class FileLeaf(
        override val name: String,
        override val uriString: String
    ) : FileNode()

    data class Directory(
        override val name: String,
        override val uriString: String,
        val children: List<FileNode> = emptyList()
    ) : FileNode()
}

data class EditorTab(
    val id: String,
    val fileName: String,
    val uriString: String?,
    val content: String,
    val language: Language,
    val isDirty: Boolean = false
)

/** Which console stream a line of execution output came from. */
enum class StreamKind { STDOUT, STDERR, SYSTEM }

data class ExecutionLine(val stream: StreamKind, val text: String)

enum class ToolchainState { NOT_INSTALLED, DOWNLOADING, INSTALLED, ERROR }

data class ToolchainInfo(
    val id: String,
    val displayName: String,
    /** Left blank for the built-in catalog entries — see LanguageManager's
     *  class doc for why no binaries are bundled by default. */
    val defaultDownloadUrl: String,
    val executableRelPath: String,
    val state: ToolchainState = ToolchainState.NOT_INSTALLED,
    val progress: Float = 0f,
    val error: String? = null
)

enum class AiProvider { ANTHROPIC, OPENAI, OLLAMA_LOCAL }

enum class AiAction(val label: String) {
    EXPLAIN("Explain Code"),
    REFACTOR("Refactor Code"),
    FIX_BUGS("Fix Bugs"),
    GENERATE_TESTS("Generate Unit Tests"),
    COMPLETE("Complete Code")
}

data class SettingsSnapshot(
    val anthropicKey: String,
    val openAiKey: String,
    val ollamaUrl: String,
    val provider: AiProvider,
    val model: String
)
