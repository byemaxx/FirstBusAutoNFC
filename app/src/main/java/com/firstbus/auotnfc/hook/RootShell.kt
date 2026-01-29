package com.firstbus.auotnfc.hook

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

internal object RootShell {

    private val suCandidates = listOf(
        // PATH
        "su",
        // Common locations (Magisk / KSU / legacy)
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
        "/data/adb/magisk/busybox/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ksud/bin/su",
        "/data/adb/ap/bin/su",
        "/magisk/.core/bin/su"
    )

    @Volatile
    private var cachedHasRoot: Boolean? = null

    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * Check if root (`su`) is available and granted.
     * This will trigger a superuser prompt on first call on some managers.
     */
    fun hasRoot(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cachedHasRoot?.let { return it }
        val result = exec("id", timeoutMs = 2_000L)
        val ok = result?.isSuccess == true && result.stdout.contains("uid=0")
        cachedHasRoot = ok
        return ok
    }

    /**
     * Try run a command with root (`su -c`).
     * Returns null if `su` is not available.
     */
    fun exec(command: String, timeoutMs: Long = 3_000L): ExecResult? {
        for (su in suCandidates) {
            val process = runCatching {
                ProcessBuilder(su, "-c", command)
                    .redirectErrorStream(false)
                    .start()
            }.getOrNull() ?: continue
            return runProcess(process, timeoutMs)
        }

        // Last resort: run through system shell (PATH may differ)
        val escaped = command.replace("\\", "\\\\").replace("\"", "\\\"")
        val shellProcess = runCatching {
            ProcessBuilder("/system/bin/sh", "-c", "su -c \"$escaped\"")
                .redirectErrorStream(false)
                .start()
        }.getOrNull() ?: return null

        return runProcess(shellProcess, timeoutMs)
    }

    private fun runProcess(process: Process, timeoutMs: Long): ExecResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val outThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.forEachLine { line -> stdout.append(line).append('\n') }
                }
            }
        }.apply { isDaemon = true }

        val errThread = Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.forEachLine { line -> stderr.append(line).append('\n') }
                }
            }
        }.apply { isDaemon = true }

        outThread.start()
        errThread.start()

        val finished = runCatching { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }.getOrNull() == true
        if (!finished) {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            runCatching { outThread.join(200) }
            runCatching { errThread.join(200) }
            return ExecResult(exitCode = -1, stdout = stdout.toString(), stderr = "timeout")
        }

        runCatching { outThread.join(200) }
        runCatching { errThread.join(200) }
        return ExecResult(exitCode = process.exitValue(), stdout = stdout.toString(), stderr = stderr.toString())
    }
}
