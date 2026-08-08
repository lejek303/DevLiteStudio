package com.devlite.studio.data

import android.content.Context
import com.devlite.studio.model.ToolchainInfo
import com.devlite.studio.model.ToolchainState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Manages offline language toolchains: downloading an archive the user
 * points at, unpacking it into app-private storage, and marking the
 * resulting binaries executable so ExecutionEngine can run them.
 *
 * IMPORTANT SCOPE NOTE: this class only handles *transport and
 * unpacking*. It does not, and cannot, ship real compiler binaries
 * (Python/Node/GCC/rustc/Go/JDK) itself — those are multi-hundred-
 * megabyte, Android-ABI-specific archives. `defaultDownloadUrl` is
 * left blank for every built-in entry; point it at a real mirror you
 * trust (e.g. a self-hosted build, or Termux's package archives) via
 * the in-app "Add a custom package" field, or by editing
 * defaultCatalog() below.
 */
class LanguageManager(private val context: Context) {

    private val client = OkHttpClient()
    private val toolchainsRoot = File(context.filesDir, "toolchains")

    private val _toolchains = MutableStateFlow(defaultCatalog())
    val toolchains: StateFlow<Map<String, ToolchainInfo>> = _toolchains

    fun installDir(toolchainId: String): File = File(toolchainsRoot, toolchainId)

    fun isInstalled(toolchainId: String): Boolean =
        installDir(toolchainId).let { it.exists() && it.listFiles()?.isNotEmpty() == true }

    suspend fun downloadAndInstall(toolchainId: String, sourceUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            setState(toolchainId, ToolchainState.DOWNLOADING, progress = 0f)
            try {
                val target = installDir(toolchainId).apply { mkdirs() }
                val archiveFile = File(context.cacheDir, "$toolchainId-download.zip")

                val request = Request.Builder().url(sourceUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                    val body = response.body ?: error("Empty response body")
                    val total = body.contentLength().takeIf { it > 0 }
                    var written = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(archiveFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total != null) {
                                    setState(
                                        toolchainId,
                                        ToolchainState.DOWNLOADING,
                                        progress = written.toFloat() / total.toFloat()
                                    )
                                }
                            }
                        }
                    }
                }

                unzip(archiveFile, target)
                archiveFile.delete()
                markExecutablesRecursive(target)

                setState(toolchainId, ToolchainState.INSTALLED, progress = 1f)
                Result.success(Unit)
            } catch (t: Throwable) {
                setState(toolchainId, ToolchainState.ERROR, error = t.message ?: "Unknown error")
                Result.failure(t)
            }
        }

    fun uninstall(toolchainId: String) {
        installDir(toolchainId).deleteRecursively()
        setState(toolchainId, ToolchainState.NOT_INSTALLED, progress = 0f)
    }

    /** Registers a user-supplied archive as a brand-new toolchain entry (e.g. a custom GitHub mirror). */
    fun registerCustomToolchain(id: String, displayName: String, downloadUrl: String, executableRelPath: String) {
        _toolchains.update { current ->
            current + (id to ToolchainInfo(id, displayName, downloadUrl, executableRelPath))
        }
    }

    private fun unzip(archive: File, targetDir: File) {
        ZipInputStream(archive.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Guard against zip-slip path traversal from a malicious archive.
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    error("Unsafe zip entry path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun markExecutablesRecursive(dir: File) {
        val binDirNames = setOf("bin", "libexec")
        dir.walkTopDown().forEach { file ->
            if (file.isFile && (file.parentFile?.name in binDirNames || !file.name.contains("."))) {
                file.setExecutable(true, false)
            }
        }
    }

    private fun setState(id: String, state: ToolchainState, progress: Float = 0f, error: String? = null) {
        _toolchains.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to existing.copy(state = state, progress = progress, error = error))
        }
    }

    private fun defaultCatalog(): Map<String, ToolchainInfo> = listOf(
        ToolchainInfo("python", "Python 3", "", "bin/python3"),
        ToolchainInfo("node", "Node.js", "", "bin/node"),
        ToolchainInfo("clang", "Clang (C/C++)", "", "bin/clang"),
        ToolchainInfo("rust", "Rust", "", "bin/rustc"),
        ToolchainInfo("go", "Go", "", "bin/go"),
        ToolchainInfo("jdk", "OpenJDK", "", "bin/java"),
        ToolchainInfo("kotlinc", "Kotlin Compiler", "", "bin/kotlinc"),
        ToolchainInfo("webview", "HTML/CSS Preview", "", "")
    ).associateBy { it.id }
}
