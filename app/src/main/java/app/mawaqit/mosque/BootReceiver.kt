package app.mawaqit.mosque

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يشغّل التطبيق عند إقلاع الجهاز إذا فعّل المستخدم "التشغيل التلقائي" من خيارات الصفحة. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action.endsWith("QUICKBOOT_POWERON")) {
            val prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto", false)) {
                val i = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(i) } catch (_: Exception) { }
            }
        }
    }
}
