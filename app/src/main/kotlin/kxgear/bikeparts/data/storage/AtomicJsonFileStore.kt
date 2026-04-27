package kxgear.bikeparts.data.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AtomicJsonFileStore {
    private companion object {
        val pathLocks = ConcurrentHashMap<Path, Any>()

        fun lockFor(path: Path): Any = pathLocks.computeIfAbsent(path.toAbsolutePath().normalize()) { Any() }
    }

    fun read(path: Path): String? {
        if (!path.exists()) {
            return null
        }
        return try {
            path.readText()
        } catch (error: Exception) {
            throw RepositoryError.CorruptState("Failed to read JSON from $path", error)
        }
    }

    fun write(path: Path, content: String) {
        synchronized(lockFor(path)) {
            var tempFile: Path? = null
            try {
                val parent = path.parent
                parent?.createDirectories()
                tempFile =
                    if (parent == null) {
                        Files.createTempFile("${path.fileName}.", ".tmp")
                    } else {
                        Files.createTempFile(parent, "${path.fileName}.", ".tmp")
                    }
                tempFile.writeText(content)
                Files.move(
                    tempFile,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (error: Exception) {
                tempFile?.let { runCatching { Files.deleteIfExists(it) } }
                throw RepositoryError.WriteFailure("Failed to atomically write JSON to $path", error)
            }
        }
    }

    fun delete(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (error: Exception) {
            throw RepositoryError.WriteFailure("Failed to delete $path", error)
        }
    }
}
