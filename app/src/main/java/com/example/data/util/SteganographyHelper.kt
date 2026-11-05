package com.example.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

object SteganographyHelper {
    private const val END_MARKER = "##END##"

    fun embedText(bitmap: Bitmap, text: String): Bitmap? {
        val message = text + END_MARKER
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val bitLength = messageBytes.size * 8

        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        // We need 1 pixel for 3 bits (R,G,B LSB)
        // Actually, let's use 1 pixel for 3 bits, or just use R channel for simplicity to avoid alpha issues.
        // Let's use R, G, B channels. 1 pixel = 3 bits.
        // So we need bitLength / 3 pixels at least.
        if (totalPixels * 3 < bitLength) {
            return null // Image too small
        }

        // We must create a mutable copy
        val outBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(totalPixels)
        outBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var pixelIndex = 0
        var bitIndex = 0
        var channelIndex = 0 // 0=R, 1=G, 2=B

        while (bitIndex < bitLength) {
            val byteIndex = bitIndex / 8
            val bitInByte = bitIndex % 8
            val bit = (messageBytes[byteIndex].toInt() shr (7 - bitInByte)) and 1

            val currentPixel = pixels[pixelIndex]
            val a = Color.alpha(currentPixel)
            var r = Color.red(currentPixel)
            var g = Color.green(currentPixel)
            var b = Color.blue(currentPixel)

            when (channelIndex) {
                0 -> r = (r and 0xFE) or bit
                1 -> g = (g and 0xFE) or bit
                2 -> b = (b and 0xFE) or bit
            }

            pixels[pixelIndex] = Color.argb(a, r, g, b)

            channelIndex++
            if (channelIndex == 3) {
                channelIndex = 0
                pixelIndex++
            }
            bitIndex++
        }

        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    fun extractText(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val outBytes = ByteArrayOutputStream()
        var currentByte = 0
        var bitIndex = 0
        var pixelIndex = 0
        var channelIndex = 0

        var extractedText = ""

        while (pixelIndex < totalPixels) {
            val currentPixel = pixels[pixelIndex]
            val r = Color.red(currentPixel)
            val g = Color.green(currentPixel)
            val b = Color.blue(currentPixel)

            val bit = when (channelIndex) {
                0 -> r and 1
                1 -> g and 1
                2 -> b and 1
                else -> 0
            }

            currentByte = (currentByte shl 1) or bit
            bitIndex++

            channelIndex++
            if (channelIndex == 3) {
                channelIndex = 0
                pixelIndex++
            }

            if (bitIndex == 8) {
                outBytes.write(currentByte)
                currentByte = 0
                bitIndex = 0
                
                val currentString = outBytes.toString(Charsets.UTF_8.name())
                if (currentString.endsWith(END_MARKER)) {
                    return currentString.removeSuffix(END_MARKER)
                }
            }
        }
        return null // Not found or error
    }
}
