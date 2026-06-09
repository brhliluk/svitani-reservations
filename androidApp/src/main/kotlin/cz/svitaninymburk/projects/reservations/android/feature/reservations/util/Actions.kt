package cz.svitaninymburk.projects.reservations.android.feature.reservations.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import cz.svitaninymburk.projects.reservations.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qrcode.QRCode
import java.io.File

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("", text))
    Toast.makeText(context, context.getString(R.string.reservation_copied_to_clipboard), Toast.LENGTH_SHORT).show()
}

internal suspend fun shareQrCode(context: Context, qrCode: QRCode) = withContext(Dispatchers.IO) {
    runCatching {
        val cacheDir = File(context.cacheDir, "qr_codes").also { it.mkdirs() }
        val file = File(cacheDir, "payment_qr.png")
        file.writeBytes(qrCode.render().getBytes())
        val uri = FileProvider.getUriForFile(
            context,
            "cz.svitaninymburk.projects.reservations.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
