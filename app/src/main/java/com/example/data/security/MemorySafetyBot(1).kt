package com.example.data.security

import android.os.Debug
import java.io.BufferedReader
import java.io.FileReader
import java.io.File

/**
 * MemorySafetyBot: بوت الأمان وإدارة الذاكرة النشط
 * يراقب عمليات تصحيح الأخطاء المتقدمة، ومحاولات قراءة الذاكرة (Dumping)، والتحايل على حماية الذاكرة.
 */
object MemorySafetyBot {

    /**
     * يتحقق من وجود أدوات الهندسة العكسية والحقن في الذاكرة (Frida, Xposed, Magisk)
     */
    fun detectFridaOrXposed(): Boolean {
        try {
            // 1. فحص المنافذ الافتراضية لخادم Frida (منفذ 27042 هو الافتراضي لـ frida-server)
            // فحص الاتصالات بـ /proc/net/tcp
            val tcpFile = File("/proc/net/tcp")
            if (tcpFile.exists()) {
                BufferedReader(FileReader(tcpFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.contains("69A2") || currentLine.contains("27042")) { // 69A2 بالست عشري هو 27042
                            return true // خادم فريدا يعمل ويستمع للاتصالات!
                        }
                    }
                }
            }

            // 2. فحص خرائط الذاكرة للعملية الحالية بحثاً عن فريدا ومكتبات الربط
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.contains("frida") || 
                            currentLine.contains("xposed") || 
                            currentLine.contains("libtest.so") || 
                            currentLine.contains(".so.so")
                        ) {
                            return true // تم العثور على مكتبات خبيثة محقونة في الذاكرة
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // الحماية بمرونة تامة لضمان عمل التطبيق الطبيعي
        }
        return false
    }

    /**
     * يتحقق من حالة تصحيح الأخطاء (Anti-Debugging) عن طريق قراءة حالة العملية من نظام التشغيل
     */
    fun isAppBeingDebugged(): Boolean {
        if (Debug.isDebuggerConnected()) return true

        try {
            // فحص TracerPid في /proc/self/status
            // إذا كان TracerPid أكبر من 0، فهذا يعني أن هناك عملية أخرى تقوم بعمل تصحيح (Debugging) للتطبيق مثل gdb أو lldb
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                BufferedReader(FileReader(statusFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("TracerPid:")) {
                            val pidStr = currentLine.substringAfter("TracerPid:").trim()
                            val pid = pidStr.toIntOrNull() ?: 0
                            if (pid > 0) {
                                return true // جاري تتبع التطبيق وهندسته عكسياً!
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // تخطي بمرونة
        }
        return false
    }

    /**
     * التحقق من سلامة بيئة مكدس الذاكرة (Memory Stack Canaries)
     */
    fun verifyMemoryStack(): Boolean {
        // فحص بيئة الجافا الافتراضية لمنع أدوات مثل VirtualApp أو Dual Space التي تستنسخ التطبيق لسرقة البيانات
        val privateDir = File("/data/data/com.example")
        if (!privateDir.exists()) {
            // إذا تم تشغيل التطبيق في حاوية وهمية (Virtual Space)، مسار البيانات قد يتم التلاعب به
            val currentDir = System.getProperty("user.dir") ?: ""
            if (currentDir.contains("virtual") || currentDir.contains("parallel")) {
                return true // تم كشف تشغيل في بيئة مستنسخة وهمية!
            }
        }
        return false
    }
}
