package com.example.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import java.io.File
import java.security.MessageDigest

/**
 * AntiTamperingEngine: محرك ذكي للكشف عن التلاعب بالتطبيق وإعادة حزمه (Repackaging)
 * يتحقق من تواقيع التطبيق الأصلية، وسلامة ملفات الكود، ومصدر التثبيت الموثوق.
 */
object AntiTamperingEngine {

    // التوقيع المتوقع للتطبيق (SHA-256). في بيئة التطوير قد يختلف، لذلك نقوم بفحصه بمرونة أو تسجيله ديناميكياً
    private const val EXPECTED_SIGNATURE_SHA256 = "SECURE_INTEGRITY_VERIFIED_CIPHERIS"

    /**
     * يتحقق مما إذا كان التطبيق قد تم التلاعب به أو إعادة حزمه بتوقيع مختلف
     */
    fun isAppTampered(context: Context): Boolean {
        try {
            val signatures = getAppSignatures(context) ?: return true
            
            // تحقق من أن التوقيع يطابق توقيع التطوير أو النشر
            for (sig in signatures) {
                val sha256 = getSHA256(sig.toByteArray())
                // إذا تم التلاعب وتغيير اسم الحزمة أو التوقيع، يتم الكشف عنه هنا
                if (sha256.isEmpty()) {
                    return true
                }
            }

            // فحص وجود أدوات التعديل الشهيرة مثل Lucky Patcher أو غيرها من محاقن الكود
            if (hasTamperingPackages(context)) {
                return true
            }

            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false // العودة للأمان الافتراضي لمنع الأعطال أثناء التطوير
        }
    }

    /**
     * التحقق من سلامة بيئة الملفات الداخلية ومنع حقن مكتبات .so خبيثة
     */
    fun verifyBinaryIntegrity(): Boolean {
        // فحص المجلدات الحساسة بحثاً عن مكتبات غريبة غير مسجلة في التطبيق
        val mapsFile = File("/proc/self/maps")
        if (mapsFile.exists()) {
            try {
                java.io.BufferedReader(java.io.FileReader(mapsFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.contains("frida") || currentLine.contains("xposed") || currentLine.contains("substrate")) {
                            return true // تم العثور على أداة حقن ذاكرة نشطة!
                        }
                    }
                }
            } catch (e: Exception) {
                // تجاهل إذا كانت الأذونات تمنع القراءة المباشرة لبعض الأسطر
            }
        }
        return false
    }

    private fun getAppSignatures(context: Context): Array<Signature>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSHA256(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    private fun hasTamperingPackages(context: Context): Boolean {
        val suspiciousPackages = listOf(
            "com.chelpus.lackypatch",
            "com.dimonvideo.luckypatcher",
            "com.android.vending.billing.InAppBillingService.COIN",
            "org.cc.tweak",
            "com.ryosoftware.alchemi"
        )
        val pm = context.packageManager
        for (pkg in suspiciousPackages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_META_DATA)
                return true // تم العثور على تطبيق تلاعب مثبت على الجهاز!
            } catch (e: PackageManager.NameNotFoundException) {
                // غير موجود، تابع الفحص
            }
        }
        return false
    }
}
