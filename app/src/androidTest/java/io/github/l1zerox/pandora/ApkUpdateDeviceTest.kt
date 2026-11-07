package io.github.l1zerox.pandora

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ApkUpdateDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun updateEntryIsPrivateAndInstallerPermissionIsDeclared() {
        val pm=context.packageManager
        assertFalse(pm.getActivityInfo(ComponentName(context,AppUpdateActivity::class.java),0).exported)
        val provider=pm.getProviderInfo(ComponentName(context,UpdateFileProvider::class.java),0)
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        assertTrue(pm.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().contains(Manifest.permission.REQUEST_INSTALL_PACKAGES))
    }
    @Test fun onlyUpdateDirectoryIsSharedAsReadOnlyContentUri() {
        val root=context.getExternalFilesDir(null)!!
        val file=File(root,"updates/provider-test-${System.nanoTime()}.apk")
        try {
            file.parentFile!!.mkdirs(); file.writeBytes(byteArrayOf(1,2,3))
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
            assertEquals("content",uri.scheme)
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(byteArrayOf(1,2,3),it.readBytes()) }
            assertTrue(runCatching { FileProvider.getUriForFile(context,"${context.packageName}.updates",File(root,"private.txt")) }.isFailure)
        } finally { file.delete() }
    }
}
