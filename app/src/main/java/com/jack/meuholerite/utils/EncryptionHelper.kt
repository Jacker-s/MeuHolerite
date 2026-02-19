package com.jack.meuholerite.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun generateKey(userId: String): SecretKey {
        // Deriva uma chave de 256 bits a partir do UID do usuário usando SHA-256
        val md = MessageDigest.getInstance("SHA-256")
        val keyBytes = md.digest(userId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(data: String, userId: String): String? {
        if (data.isEmpty()) return data
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, generateKey(userId))
            val iv = cipher.iv // GCM usa 12 bytes de IV por padrão
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(encryptedData: String, userId: String): String? {
        if (encryptedData.isEmpty()) return encryptedData
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, generateKey(userId), spec)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
