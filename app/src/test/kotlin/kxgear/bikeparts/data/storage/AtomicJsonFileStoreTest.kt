package kxgear.bikeparts.data.storage

import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicJsonFileStoreTest {
    @Test
    fun concurrentWritesToSamePathDoNotReuseTempFile() = runBlocking {
        val root = Files.createTempDirectory("atomic-store-concurrent")
        val target = root.resolve("bike.json")

        val writes =
            (1..50).map { index ->
                async(Dispatchers.Default) {
                    AtomicJsonFileStore().write(target, """{"value":$index}""")
                }
            }

        writes.awaitAll()

        assertTrue(target.readText().startsWith("""{"value":"""))
        assertEquals(emptyList<String>(), root.listDirectoryEntries("*.tmp").map { it.fileName.toString() })
    }
}
