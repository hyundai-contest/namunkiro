package com.namunkiro.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * kotlinx.serialization JSON 파일 저장소. 키 하나 = 파일 하나(`<dir>/<key>.json`).
 * 원자적 쓰기: 임시 파일에 쓰고 fsync → 같은 디렉터리 안에서 ATOMIC_MOVE로 교체.
 * 쓰기 도중 앱이 죽어도 이전 파일이나 새 파일 중 하나만 남는다.
 * Android 의존 없음(단위 테스트에서 임시 폴더로 사용 가능).
 */
class JsonFileStore(
    private val dir: File,
    val json: Json = DefaultJson,
) {
    private val lock = Any()

    init {
        dir.mkdirs()
    }

    fun <T> read(key: String, serializer: KSerializer<T>): T? = synchronized(lock) {
        val f = file(key)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(serializer, f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            // 손상된 파일은 옆에 남겨 두고(디버깅용) 없는 것으로 취급한다
            runCatching { f.copyTo(File(dir, "$key.corrupt.json"), overwrite = true) }
            null
        }
    }

    fun <T> write(key: String, serializer: KSerializer<T>, value: T) = synchronized(lock) {
        val text = json.encodeToString(serializer, value)
        val tmp = File(dir, "$key.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file(key).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            // ATOMIC_MOVE 미지원 파일시스템 대비
            Files.move(tmp.toPath(), file(key).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete(key: String) = synchronized(lock) { file(key).delete() }

    fun clearAll() = synchronized(lock) {
        dir.listFiles()?.filter { it.name.endsWith(".json") || it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, "$key.json")

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
