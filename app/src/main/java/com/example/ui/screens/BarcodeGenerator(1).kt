package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

fun generateQrCodeBitmap(
    text: String,
    size: Int = 512,
    startColorHex: String = "#00897B", // SecureGreen
    midColorHex: String = "#1D4ED8",   // SecureCyan
    endColorHex: String = "#DC2626",   // DangerRed
    backgroundHex: String = "#FFFFFF"  // Pure White
): ImageBitmap {
    return generateRawQrCodeBitmap(text, size, startColorHex, midColorHex, endColorHex, backgroundHex).asImageBitmap()
}

fun generateRawQrCodeBitmap(
    text: String,
    size: Int = 512,
    startColorHex: String = "#00897B",
    midColorHex: String = "#1D4ED8",
    endColorHex: String = "#DC2626",
    backgroundHex: String = "#FFFFFF"
): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = try {
        writer.encode(text, BarcodeFormat.QR_CODE, size, size)
    } catch (e: Exception) {
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    val startColor = Color.parseColor(startColorHex)
    val midColor = Color.parseColor(midColorHex)
    val endColor = Color.parseColor(endColorHex)
    val backgroundColor = Color.parseColor(backgroundHex)
    
    for (x in 0 until width) {
        for (y in 0 until height) {
            if (bitMatrix.get(x, y)) {
                val ratio = (x + y).toFloat() / (width + height).toFloat()
                val r: Int
                val g: Int
                val b: Int
                if (ratio < 0.5f) {
                    val subRatio = ratio * 2f
                    r = (Color.red(startColor) * (1 - subRatio) + Color.red(midColor) * subRatio).toInt()
                    g = (Color.green(startColor) * (1 - subRatio) + Color.green(midColor) * subRatio).toInt()
                    b = (Color.blue(startColor) * (1 - subRatio) + Color.blue(midColor) * subRatio).toInt()
                } else {
                    val subRatio = (ratio - 0.5f) * 2f
                    r = (Color.red(midColor) * (1 - subRatio) + Color.red(endColor) * subRatio).toInt()
                    g = (Color.green(midColor) * (1 - subRatio) + Color.green(endColor) * subRatio).toInt()
                    b = (Color.blue(midColor) * (1 - subRatio) + Color.blue(endColor) * subRatio).toInt()
                }
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            } else {
                bitmap.setPixel(x, y, backgroundColor)
            }
        }
    }
    return bitmap
}

fun shareQrCodeImage(context: Context, textContent: String, title: String = "Cipheris QR Code", shareText: String? = null) {
    try {
        val bitmap = generateRawQrCodeBitmap(textContent, 600)
        val cachePath = File(context.cacheDir, "shared_qr_codes")
        if (!cachePath.exists()) cachePath.mkdirs()
        
        val file = File(cachePath, "cipheris_qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (!shareText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "مشاركة صورة الباركود (QR Code)"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل مشاركة الباركود: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

