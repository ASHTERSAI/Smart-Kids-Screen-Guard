package id.smartkids.screenguard.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

data class DeviceSecurityState(
    val isDeviceAdminActive: Boolean,
    val isDeviceOwnerActive: Boolean,
    val isUninstallBlocked: Boolean,
    val protectionModeSummary: String
)

class DeviceOwnerManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, ScreenGuardDeviceAdminReceiver::class.java)

    fun getSecurityState(): DeviceSecurityState {
        val isAdmin = dpm.isAdminActive(adminComponent)
        val isOwner = dpm.isDeviceOwnerApp(context.packageName)
        val uninstallBlocked = if (isOwner) {
            dpm.isUninstallBlocked(adminComponent, context.packageName)
        } else {
            false
        }
        val summary = when {
            isOwner && uninstallBlocked ->
                "Mode Device Owner Aktif: Uninstall diblokir sepenuhnya di level OS Android."
            isOwner && !uninstallBlocked ->
                "Mode Device Owner Aktif: Kebijakan blokir uninstall dapat diaktifkan."
            isAdmin ->
                "Mode Device Admin Biasa (APK Standar): Mencegah uninstall langsung sampai Device Admin dinonaktifkan di Pengaturan."
            else ->
                "Instalasi APK Standar: Belum mengaktifkan Device Admin / Device Owner."
        }
        return DeviceSecurityState(
            isDeviceAdminActive = isAdmin,
            isDeviceOwnerActive = isOwner,
            isUninstallBlocked = uninstallBlocked,
            protectionModeSummary = summary
        )
    }

    fun createEnableDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Aktifkan perlindungan agar anak tidak dapat menghapus Smart Kids Screen Guard dengan mudah."
            )
        }
    }

    fun setUninstallProtection(blockUninstall: Boolean): Result<String> {
        return try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, blockUninstall)
                val status = if (blockUninstall) "diaktifkan" else "dilepaskan"
                Result.success("Blokir uninstall Device Owner berhasil $status.")
            } else {
                Result.failure(
                    IllegalStateException(
                        "Perangkat menggunakan instalasi APK biasa (bukan Device Owner). Gunakan tombol Aktifkan Device Admin atau jalankan perintah ADB dpm set-device-owner."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun releaseAllProtections(): Result<String> {
        return try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
            }
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
            }
            Result.success("Seluruh proteksi Device Owner & Device Admin telah dilepaskan secara resmi. Aplikasi kini dapat dihapus normal.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
