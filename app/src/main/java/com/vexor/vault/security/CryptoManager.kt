package com.vexor.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {
    
    private const val KEYSTORE_ALIAS = "vexor_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        // Return existing key if available
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { 
            return it as SecretKey 
        }
        
        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
    
    fun encrypt(plainText: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        
        val encryptedBytes = cipher.doFinal(plainText)
        
        return EncryptedData(
            cipherText = encryptedBytes,
            iv = cipher.iv
        )
    }
    
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        
        return cipher.doFinal(encryptedData.cipherText)
    }
    
    fun encryptString(plainText: String): String {
        val encrypted = encrypt(plainText.toByteArray(Charsets.UTF_8))
        val combined = encrypted.iv + encrypted.cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    fun decryptString(encryptedText: String): String {
        val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
        val cipherText = combined.sliceArray(GCM_IV_LENGTH until combined.size)
        
        val decrypted = decrypt(EncryptedData(cipherText, iv))
        return String(decrypted, Charsets.UTF_8)
    }
    
    data class EncryptedData(
        val cipherText: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedData
            return cipherText.contentEquals(other.cipherText) && iv.contentEquals(other.iv)
        }
        
        override fun hashCode(): Int {
            var result = cipherText.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }
}
