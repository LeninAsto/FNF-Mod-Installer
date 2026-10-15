package androidx.documentfile.provider

import android.net.Uri
import com.leninasto.fnfmodinstaler.createModDirectory
import com.leninasto.fnfmodinstaler.deleteIncompleteInstallation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DirectInstallationTest {
    @Test fun createsTheFinalModNameWithoutRenaming() {
        val root = FakeFolder("mods")
        val mod = createModDirectory(root, "My Mod")!!
        assertEquals("My Mod", mod.name)
        assertEquals(listOf("My Mod"), root.children.map { it.name })
    }

    @Test fun duplicateNamesPreserveTheExistingMod() {
        val existing = FakeFolder("My Mod")
        val root = FakeFolder("mods", mutableListOf(existing, FakeFolder("My Mod (2)")))
        assertEquals("My Mod (3)", createModDirectory(root, "My Mod")!!.name)
        assertSame(existing, root.children.first())
    }

    @Test fun cleansIncompleteFoldersWhenProviderRequiresDeletingChildrenFirst() = runBlocking {
        val child = FakeFolder("data")
        val root = FakeFolder("partial mod", mutableListOf(child), requiresEmpty = true)
        deleteIncompleteInstallation(root)
        assertTrue(child.deleted)
        assertTrue(root.deleted)
    }

    @Test fun usesProviderRecursiveDeleteWithoutListingTheFolder() = runBlocking {
        val root = FakeFolder(".fnf-install-old", listingAllowed = false)
        deleteIncompleteInstallation(root)
        assertTrue(root.deleted)
    }

    private class FakeFolder(
        private val folderName: String,
        val children: MutableList<FakeFolder> = mutableListOf(),
        private val requiresEmpty: Boolean = false,
        private val listingAllowed: Boolean = true
    ) : DocumentFile(null) {
        var deleted = false
        override fun getName() = folderName
        override fun getUri(): Uri = error("URI access not needed")
        override fun getType() = "vnd.android.document/directory"
        override fun isDirectory() = true
        override fun isFile() = false
        override fun isVirtual() = false
        override fun lastModified() = 0L
        override fun length() = 0L
        override fun canRead() = true
        override fun canWrite() = true
        override fun exists() = !deleted
        override fun renameTo(displayName: String): Boolean = error("Installation must not rename")
        override fun createFile(mimeType: String, displayName: String): DocumentFile? = error("Not needed")
        override fun createDirectory(displayName: String): DocumentFile = FakeFolder(displayName).also { children.add(it) }
        override fun listFiles(): Array<DocumentFile> {
            check(listingAllowed) { "Provider cannot enumerate this folder" }
            return children.filterNot { it.deleted }.toTypedArray()
        }
        override fun delete(): Boolean {
            if (requiresEmpty && children.any { !it.deleted }) return false
            deleted = true
            return true
        }
    }
}
