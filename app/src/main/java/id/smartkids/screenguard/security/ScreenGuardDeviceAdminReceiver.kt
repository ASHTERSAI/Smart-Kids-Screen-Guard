package id.smartkids.screenguard.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ScreenGuardDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Perlindungan Device Admin Smart Kids Screen Guard Aktif.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Peringatan Keamanan Orang Tua: Menonaktifkan perlindungan ini akan membuka akses penghapusan aplikasi."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Perlindungan Device Admin dinonaktifkan.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
