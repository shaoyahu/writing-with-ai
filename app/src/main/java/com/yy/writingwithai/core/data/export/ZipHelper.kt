package com.yy.writingwithai.core.data.export

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

    /** 从 [inputStream] 读 zip;返回 文件名 → 内容。 */
    fun readZip(inputStream: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }
}
