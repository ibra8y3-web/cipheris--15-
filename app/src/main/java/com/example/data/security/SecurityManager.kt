package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

class SecurityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_vault_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setMasterPin(pin: String) {
        sharedPreferences.edit().putString("master_pin", pin).apply()
    }

    fun getMasterPin(): String? {
        return sharedPreferences.getString("master_pin", null)
    }

    fun setPanicPin(pin: String) {
        sharedPreferences.edit().putString("panic_pin", pin).apply()
    }

    fun getPanicPin(): String? {
        return sharedPreferences.getString("panic_pin", null)
    }

    fun getDatabasePassphrase(): String {
        var passphrase = sharedPreferences.getString("db_passphrase", null)
        if (passphrase == null) {
            passphrase = generateRandomPassphrase()
            sharedPreferences.edit().putString("db_passphrase", passphrase).apply()
        }
        return passphrase
    }

    private fun generateRandomPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    fun clearAllData(context: Context) {
        sharedPreferences.edit().clear().apply()
        context.deleteDatabase("cipheris_secure_chat.db")
        // Additional cleanup like clearing shared prefs of other components
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
