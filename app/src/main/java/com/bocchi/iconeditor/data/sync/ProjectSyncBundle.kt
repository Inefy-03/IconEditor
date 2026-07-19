package com.bocchi.iconeditor.data.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Uncompressed multi-file payload for LAN sync.
 * Avoids zip compress/decompress of already-compressed PNGs.
 *
 * Layout (big-endian):
 *   magic "IEBNDL01"
 *   u32 entryCount
 *   repeat:
 *     u16 nameLen + utf8 name
 *     u32 dataLen + bytes
 */
object ProjectSyncBundle {
    const val CONTENT_TYPE = "application/x-ie-bundle"
    private val MAGIC = "IEBNDL01".toByteArray(StandardCharsets.US_ASCII)

    fun encode(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream(entries.sumOf { it.second.size + 64 } + 16)
        DataOutputStream(out).use { dos ->
            dos.write(MAGIC)
            dos.writeInt(entries.size)
            for ((name, bytes) in entries) {
                val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
                require(nameBytes.size <= 0xFFFF) { "文件名过长：$name" }
                dos.writeShort(nameBytes.size)
                dos.write(nameBytes)
                dos.writeInt(bytes.size)
                dos.write(bytes)
            }
        }
        return out.toByteArray()
    }

    fun encodeFiles(files: List<File>, nameOf: (File) -> String = { it.name }): ByteArray {
        return encode(files.map { nameOf(it) to it.readBytes() })
    }

    fun decode(data: ByteArray): List<Pair<String, ByteArray>> {
        require(data.size >= MAGIC.size + 4) { "无效同步包" }
        DataInputStream(ByteArrayInputStream(data)).use { dis ->
            val magic = ByteArray(MAGIC.size)
            dis.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "同步包格式不匹配（请两端都更新到最新版）" }
            val count = dis.readInt()
            require(count in 0..10_000) { "同步包条目数异常：$count" }
            val result = ArrayList<Pair<String, ByteArray>>(count)
            repeat(count) {
                val nameLen = dis.readUnsignedShort()
                val nameBytes = ByteArray(nameLen)
                dis.readFully(nameBytes)
                val name = String(nameBytes, StandardCharsets.UTF_8)
                val dataLen = dis.readInt()
                require(dataLen in 0..50_000_000) { "同步包文件过大：$name" }
                val bytes = ByteArray(dataLen)
                dis.readFully(bytes)
                result += name to bytes
            }
            return result
        }
    }

    fun isBundle(data: ByteArray): Boolean =
        data.size >= MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
}
