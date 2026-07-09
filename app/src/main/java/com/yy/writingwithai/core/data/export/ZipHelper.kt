package com.yy.writingwithai.core.data.export

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-3 · zip 读写 helper(JDK `java.util.zip` 内置，不引第三方库)。
 *
 * 支持 `OutputStream` / `InputStream`(适配 SAF `contentResolver.openOutputStream(uri)`)。
 *
 * fix-2026-06-24-review-r1-critical · `readZip` 防 Zip Slip:
 * - 每 entry resolve 到 `File(targetDir, entry.name)`，再 `.canonicalPath`
 * - 必须等于 `targetDir.canonicalPath` 或以 `${targetDir.canonicalPath}/` 开头
 * - 违反 → 抛 [ImportRejected]，不写任何字节
 *
 * fix M5 (full-review):防 zip bomb(解压膨胀攻击):
 * - 单 entry 解压后字节数上限 [MAX_ENTRY_BYTES](100 MiB)
 * - 所有 entry 解压后总字节数上限 [MAX_TOTAL_BYTES](500 MiB)
 * - 任意一项超限 → 抛 [ImportRejected]
 */
@Singleton
class ZipHelper
@Inject
constructor() {
    /** 写 zip 到 [outputStream];entries = 文件名 → 内容。 */
    fun writeZip(entries: Map<String, ByteArray>, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /**
     * 从 [inputStream] 读 zip;返回 文件名 → 内容。
     *
     * 默认 `virtualRoot = File(".")`(当前工作目录，纯内存校验，不实际写盘)。
     *
     * @throws ImportRejected 任一 entry 路径穿越 / 绝对路径 / 含 `..` 段
     */
    fun readZip(inputStream: InputStream): Map<String, ByteArray> = readZip(inputStream, virtualRoot = File("."))

    /**
     * 从 [inputStream] 读 zip;逐 entry resolve 到 [virtualRoot] 之下做 canonical containment check。
     * 不实际写盘 — 仅做内存校验，返回文件名 → 字节内容。
     *
     * review r2 修:Android 上 `File.canonicalPath` 可能以 `//` 开头(如 `//data/...`),
     * 导致 `startsWith("$rootPath/")` 比较失败，拒绝所有合法 entry。改用
     * `resolved.relativeToOrNull(rootCanonical)` 判断是否在 root 之下 — 语义更清晰，
     * 且不受路径前缀格式差异影响。
     */
    fun readZip(inputStream: InputStream, virtualRoot: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        val rootCanonical = virtualRoot.canonicalFile
        var totalBytes = 0L
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // 1. 名字本身不能用绝对路径或 `..` 段(防御层)
                if (entry.name.contains("..") || File(entry.name).isAbsolute) {
                    throw ImportRejected("Zip entry name escapes target: ${entry.name}")
                }
                // 2. canonical 必须在 root 之下(主防御层)
                //    relativeToOrNull 返回非 null → resolved 是 root 的后代(含自身);
                //    返回 null → resolved 在 root 之外(zip slip)。
                val resolved = File(virtualRoot, entry.name).canonicalFile
                val relative = resolved.relativeToOrNull(rootCanonical)
                if (relative == null) {
                    throw ImportRejected("Zip entry resolves outside target: ${entry.name} -> ${resolved.path}")
                }
                // entry 是目录时跳过(不读内容)，但仍然通过上面的 containment check。
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    // fix M5 (full-review):zip bomb 防护 — 单 entry 解压后字节数检查。
                    if (bytes.size > MAX_ENTRY_BYTES) {
                        throw ImportRejected(
                            "Zip entry too large (${bytes.size} > $MAX_ENTRY_BYTES): ${entry.name}"
                        )
                    }
                    totalBytes += bytes.size
                    // fix M5 (full-review):zip bomb 防护 — 累计解压字节数检查。
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw ImportRejected(
                            "Zip total decompressed size exceeds $MAX_TOTAL_BYTES bytes"
                        )
                    }
                    result[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    companion object {
        // fix M5 (full-review):zip bomb 防护阈值。
        // 100 MiB per-entry 上限足够覆盖 M4-3 导出格式内单文件最大值
        // (NoteExporter JSON + 嵌入图片 base64 后一般 < 10 MiB)。
        private const val MAX_ENTRY_BYTES: Int = 100 * 1024 * 1024

        // 500 MiB 累计上限防止攻击者用大量 entry 撑爆内存。
        private const val MAX_TOTAL_BYTES: Long = 500L * 1024 * 1024
    }
}
