package com.devlite.studio.data

import android.content.Context
import com.devlite.studio.model.ExecutionLine
import com.devlite.studio.model.Language
import com.devlite.studio.model.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Runs a source file with the appropriate offline toolchain via
 * ProcessBuilder, streaming stdout/stderr back as a Flow. Also exposes
 * a stdin writer so the terminal's input bar can send interactive
 * input to whatever is currently running.
 */
class ExecutionEngine(
    private val context: Context,
    private val languageManager: LanguageManager
) {
    private var activeProcess: Process? = null

    fun run(sourceFile: File, language: Language): Flow<ExecutionLine> = callbackFlow {
        val toolchainId = language.toolchainId

        if (language == Language.HTML || language == Language.CSS) {
            trySend(ExecutionLine(StreamKind.SYSTEM, "Use the WebView preview for ${sourceFile.name} instead of the console."))
            close()
            return@callbackFlow
        }

        if (!languageManager.isInstalled(toolchainId)) {
            trySend(ExecutionLine(StreamKind.SYSTEM, "No ${language.displayName} toolchain installed — open the Language Manager to add one."))
            close()
            return@callbackFlow
        }

        val toolchainDir = languageManager.installDir(toolchainId)
        val command = buildCommand(language, sourceFile, toolchainDir)
        if (command == null) {
            trySend(ExecutionLine(StreamKind.SYSTEM, "Don't know how to run .${sourceFile.extension} files."))
            close()
            return@callbackFlow
        }

        trySend(ExecutionLine(StreamKind.SYSTEM, "$ ${command.joinToString(" ")}"))

        val process = try {
            ProcessBuilder(command)
                .directory(sourceFile.parentFile)
                .redirectErrorStream(false)
                .apply {
                    environment()["PATH"] =
                        "${File(toolchainDir, "bin").absolutePath}:${System.getenv("PATH").orEmpty()}"
                    environment()["HOME"] = context.filesDir.absolutePath
                }
                .start()
        } catch (t: Throwable) {
            trySend(ExecutionLine(StreamKind.STDERR, "Failed to start process: ${t.message}"))
            close()
            return@callbackFlow
        }

        activeProcess = process

        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

        val stdoutThread = Thread {
            stdoutReader.forEachLineSafely { line -> trySend(ExecutionLine(StreamKind.STDOUT, line)) }
        }
        val stderrThread = Thread {
            stderrReader.forEachLineSafely { line -> trySend(ExecutionLine(StreamKind.STDERR, line)) }
        }
        stdoutThread.start()
        stderrThread.start()

        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()
        trySend(ExecutionLine(StreamKind.SYSTEM, "Process exited with code $exitCode"))

        activeProcess = null
        close()

        awaitClose {
            process.destroyForcibly()
            activeProcess = null
        }
    }.flowOn(Dispatchers.IO)

    /** Sends a line of text to the running process's stdin — wired to the terminal's input bar. */
    fun sendInput(text: String) {
        val process = activeProcess ?: return
        try {
            OutputStreamWriter(process.outputStream).apply {
                write(text + "\n")
                flush()
            }
        } catch (_: Throwable) {
            // Process likely already exited; nothing to send to.
        }
    }

    fun stop() {
        activeProcess?.destroyForcibly()
        activeProcess = null
    }

    private fun BufferedReader.forEachLineSafely(action: (String) -> Unit) {
        try {
            var line = readLine()
            while (line != null) {
                action(line)
                line = readLine()
            }
        } catch (_: Throwable) {
            // Stream closes when the process ends; nothing more to read.
        }
    }

    private fun buildCommand(language: Language, file: File, toolchainDir: File): List<String>? {
        fun bin(name: String) = File(toolchainDir, "bin/$name").absolutePath
        val path = file.absolutePath
        val outBinary = File(file.parentFile, file.nameWithoutExtension).absolutePath

        return when (language) {
            Language.PYTHON -> listOf(bin("python3"), "-u", path)
            Language.JAVASCRIPT -> listOf(bin("node"), path)
            Language.TYPESCRIPT -> listOf(bin("node"), bin("tsc"), path)
            Language.C -> listOf("sh", "-c", "'${bin("clang")}' '$path' -o '$outBinary' && '$outBinary'")
            Language.CPP -> listOf("sh", "-c", "'${bin("clang++")}' '$path' -o '$outBinary' && '$outBinary'")
            Language.RUST -> listOf("sh", "-c", "'${bin("rustc")}' '$path' -o '$outBinary' && '$outBinary'")
            Language.GO -> listOf(bin("go"), "run", path)
            Language.JAVA -> listOf(
                "sh", "-c",
                "'${bin("javac")}' '$path' && '${bin("java")}' -cp '${file.parentFile?.absolutePath}' '${file.nameWithoutExtension}'"
            )
            Language.KOTLIN -> listOf(
                "sh", "-c",
                "'${bin("kotlinc")}' '$path' -include-runtime -d '$outBinary.jar' && '${bin("java")}' -jar '$outBinary.jar'"
            )
            else -> null
        }
    }
}
