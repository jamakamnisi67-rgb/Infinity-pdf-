package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ToolRegistry
import com.example.domain.pdf.FileExportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Infinity PDF", appName)
    }

    @Test
    fun `verify all tools registered`() {
        val tools = ToolRegistry.ALL_TOOLS
        assertTrue("Expected at least 30 tools", tools.size >= 30)

        // Verify key tools exist
        assertNotNull(ToolRegistry.getToolById("compress"))
        assertNotNull(ToolRegistry.getToolById("merge"))
        assertNotNull(ToolRegistry.getToolById("split"))
        assertNotNull(ToolRegistry.getToolById("rotate"))
        assertNotNull(ToolRegistry.getToolById("delete_pages"))
        assertNotNull(ToolRegistry.getToolById("extract_pages"))
        assertNotNull(ToolRegistry.getToolById("organize"))
        assertNotNull(ToolRegistry.getToolById("pdf_to_word"))
        assertNotNull(ToolRegistry.getToolById("word_to_pdf"))
        assertNotNull(ToolRegistry.getToolById("jpg_to_pdf"))
        assertNotNull(ToolRegistry.getToolById("scanner"))
    }

    @Test
    fun `verify file size formatting`() {
        assertEquals("0 B", FileExportHelper.formatFileSize(0))
        assertEquals("500.0 B", FileExportHelper.formatFileSize(500))
        assertEquals("1.0 KB", FileExportHelper.formatFileSize(1024))
        assertEquals("2.5 MB", FileExportHelper.formatFileSize(2621440))
    }
}
