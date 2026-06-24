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
 * M4-3 · zip 读写 helper(JDK `java.util.zip` 内置,不引第三方库)。
 *
 * 支持 `OutputStream` / `InputStream`(适配 SAF `contentResolver.openOutputStream(uri)`)。
 *
 * fix-2026-06-24-review-r1-critical · `readZip` 防 Zip Slip:
 * - 每 entry resolve 到 `File(targetDir, entry.name)`,再 `.canonicalPath`
 * - 必须等于 `targetDir.canonicalPath` 或以 `${targetDir.canonicalPath}/` 开头
 * - 违反 → 抛 [ImportRejected],不写任何字节
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
     * 默认 `virtualRoot = File(".")`(当前工作目录,纯内存校验,不实际写盘)。
     *
     * @throws ImportRejected 任一 entry 路径穿越 / 绝对路径 / 含 `..` 段
     */
    fun readZip(inputStream: InputStream): Map<String, ByteArray> = readZip(inputStream, virtualRoot = File("."))

    /**
     * 从 [inputStream] 读 zip;逐 entry resolve 到 [virtualRoot] 之下做 canonical containment check。
     * 不实际写盘 — 仅做内存校验,返回文件名 → 字节内容。
     */
    fun readZip(inputStream: InputStream, virtualRoot: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        val rootCanonical = virtualRoot.canonicalFile
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // 1. 名字本身不能用绝对路径或 `..` 段(防御层)
                if (entry.name.contains("..") || File(entry.name).isAbsolute) {
                    throw ImportRejected("Zip entry name escapes target: ${entry.name}")
                }
                // 2. canonical 必须在 root 之下(主防御层)
                val resolved = File(virtualRoot, entry.name).canonicalFile
                val resolvedPath = resolved.path
                val rootPath = rootCanonical.path
                if (resolvedPath != rootPath && !resolvedPath.startsWith("$rootPath${File.separator}")) {
                    throw ImportRejected("Zip entry resolves outside target: ${entry.name} -> $resolvedPath")
                }
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }
}
