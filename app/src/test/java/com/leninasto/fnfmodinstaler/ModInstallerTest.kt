package com.leninasto.fnfmodinstaler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModInstallerTest {

    @Test
    fun normalZipUsesModFolderAsDestination() {
        val plan = planZipInstall(
            entries = listOf(
                ZipEntryInfo("cool-mod/pack.json", isDirectory = false),
                ZipEntryInfo("cool-mod/data/song.json", isDirectory = false)
            ),
            archiveName = "cool-mod.zip"
        )

        assertNotNull(plan)
        assertEquals("cool-mod", plan!!.sourceRoot)
        assertEquals("cool-mod", plan.destinationFolderName)
    }

    @Test
    fun wrappedZipSkipsFoldersBeforeMetadataRoot() {
        val plan = planZipInstall(
            entries = listOf(
                ZipEntryInfo("Mod xxxxx psych port/bin/Xxxxxx version 1.2.3/pack.json", isDirectory = false),
                ZipEntryInfo("Mod xxxxx psych port/bin/Xxxxxx version 1.2.3/images/icon.png", isDirectory = false),
                ZipEntryInfo("Mod xxxxx psych port/readme.txt", isDirectory = false)
            ),
            archiveName = "wrapped.zip"
        )

        assertNotNull(plan)
        assertEquals("Mod xxxxx psych port/bin/Xxxxxx version 1.2.3", plan!!.sourceRoot)
        assertEquals("Xxxxxx version 1.2.3", plan.destinationFolderName)
    }

    @Test
    fun rootlessZipCreatesFolderFromArchiveName() {
        val plan = planZipInstall(
            entries = listOf(
                ZipEntryInfo("pack.json", isDirectory = false),
                ZipEntryInfo("data/song.json", isDirectory = false)
            ),
            archiveName = "My Rootless Mod.zip"
        )

        assertNotNull(plan)
        assertEquals("", plan!!.sourceRoot)
        assertEquals("My Rootless Mod", plan.destinationFolderName)
    }

    @Test
    fun polymodMetadataIsAlsoDetected() {
        val plan = planZipInstall(
            entries = listOf(
                ZipEntryInfo("wrapper/Actual Polymod/_polymod_meta.json", isDirectory = false),
                ZipEntryInfo("wrapper/Actual Polymod/data/file.txt", isDirectory = false)
            ),
            archiveName = "poly.zip"
        )

        assertNotNull(plan)
        assertEquals("wrapper/Actual Polymod", plan!!.sourceRoot)
        assertEquals("Actual Polymod", plan.destinationFolderName)
    }
}
