package com.leninasto.fnfmodinstaler

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ReleaseSafetyTest {
    @Test fun rejectsTraversalAbsolutePathsAndAmbiguousNames() {
        listOf("../secret", "mod/../file", "/absolute", "C:\\file", "mod//file", "mod/./file", "mod/\u0000file", " ").forEach {
            assertNull(it, normalizeZipEntryPath(it))
        }
        assertEquals("mod/data/song.json", normalizeZipEntryPath("mod\\data\\song.json"))
        assertEquals("mod", normalizeZipEntryPath("mod/"))
    }

    @Test fun rejectsExcessiveNesting() {
        assertNull(normalizeZipEntryPath(List(33) { "dir" }.joinToString("/")))
    }

    @Test fun supportsApiRangeInsteadOfExactPatchEquality() {
        assertEquals(ModCompatibility.MATCH, assessApi("0.8.0", "0.8.4", true))
        assertEquals(ModCompatibility.MATCH, assessApi("0.8.3", "0.8.1", true))
        assertEquals(ModCompatibility.MISMATCH, assessApi("0.7.4", "0.8.4", true))
        assertEquals(ModCompatibility.MISMATCH, assessApi("0.9.0", "0.8.4", true))
    }

    @Test fun neverAssumesFutureVersionsOrPortsAreCompatible() {
        assertEquals(ModCompatibility.UNKNOWN, assessApi("0.8.4", "0.8.5", true))
        assertEquals(ModCompatibility.UNKNOWN, assessApi("0.8.4", "0.8.4", false))
        assertEquals(ModCompatibility.UNKNOWN, assessApi("0.8.4", null, true))
        assertEquals(ModCompatibility.INVALID, assessApi("garbage", "0.8.4", true))
    }

    @Test fun changelogShowsOnceAndAgainOnUpgrade() {
        assertTrue(shouldShowChangelog(0, 4))
        assertFalse(shouldShowChangelog(4, 4))
        assertTrue(shouldShowChangelog(4, 5))
        assertFalse(shouldShowChangelog(5, 4))
    }

    @Test fun bothDualStorageEnginesUseTheirSelectedDestination() {
        listOf(AppDestinations.PLUS_ENGINE, AppDestinations.PSLICE_ENGINE).forEach { engine ->
            assertEquals("${engine.name}_ALT", activeEngineFolderKey(engine, mapOf("${engine.name}_ACTIVE_IS_ALT" to "true")))
            assertEquals(engine.name, activeEngineFolderKey(engine, emptyMap()))
        }
    }

    @Test fun rejectsMultipleModsInsteadOfDiscardingOne() {
        assertNull(planZipInstall(listOf(ZipEntryInfo("a/pack.json", false), ZipEntryInfo("b/pack.json", false)), "bundle.zip"))
        assertEquals("single", planZipInstall(listOf(ZipEntryInfo("readme.txt", false)), "single.zip")!!.destinationFolderName)
        assertEquals("", planZipInstall(listOf(ZipEntryInfo("readme.txt", false)), "single.zip")!!.sourceRoot)
    }

    @Test fun capsMetadataBeforeParsing() {
        assertEquals("{}", readMetadata(ByteArrayInputStream("{}".toByteArray())))
        assertThrows(IOException::class.java) { readMetadata(ByteArrayInputStream(ByteArray(MAX_METADATA_BYTES + 1))) }
    }

    @Test fun enforcesActualDecompressedEntrySize() {
        var remaining = MAX_ENTRY_BYTES + 1
        val stream = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length.toLong()).toInt()
                remaining -= count
                return count
            }
        }
        assertThrows(IOException::class.java) { CopyBudget().copy(stream, null) }
    }

    @Test fun limitsNumberOfArchiveEntries() {
        val budget = CopyBudget()
        repeat(MAX_ARCHIVE_ENTRIES) { budget.entry() }
        assertThrows(IOException::class.java) { budget.entry() }
    }
}
