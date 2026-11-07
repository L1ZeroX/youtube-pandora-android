package io.github.l1zerox.pandora

import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtractorRuntimeTest {
    @Test fun installedZipappRunsWithBundledPythonAndReportsItsActualVersion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Engine(context) { _,_,_ -> }.init()
        val request=YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
        val version=YoutubeDL.execute(request).out.trim()
        assertFalse(shouldInstallExtractor(version))
        assertEquals(BundledExtractor.version(context),version)
        assertTrue(File(context.applicationInfo.nativeLibraryDir,"libqjs.so").isFile)
    }
}
