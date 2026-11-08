package id.smartkids.screenguard.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class ParentAuthManager(context: Context) {

    private val prefs = context.getSharedPreferences("parent_secure_vault", Context.MODE_PRIVATE)

    companion object {
        private const val KEYSTORE_ALIAS = "SmartKidsParentVaultKey"
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 menit

        fun hashSecretWithSalt(secret: String, salt: ByteArray): String {
            val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return Base64.encodeToString(hash, Base64.NO_WRAP)
        }

        fun constantTimeEquals(a: String, b: String): Boolean {
            return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
        }
    }

    fun isParentRegistered(): Boolean {
        return prefs.contains("username") && prefs.contains("password_hash") && prefs.contains("pin_hash")
    }

    fun getRegisteredUsername(): String = prefs.getString("username", "") ?: ""

    fun registerParentAccount(
        username: String,
        password: String,
        confirmPassword: String,
        pin: String,
        confirmPin: String,
        recoveryQuestionAnswer: String
    ): Result<Unit> {
        if (username.isBlank()) return Result.failure(IllegalArgumentException("Username wajib diisi."))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Password minimal 6 karakter."))
        if (password != confirmPassword) return Result.failure(IllegalArgumentException("Konfirmasi password tidak cocok."))
        if (pin.length < 6 || !pin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN wajib minimal 6 digit angka."))
        }
        if (pin != confirmPin) return Result.failure(IllegalArgumentException("Konfirmasi PIN tidak cocok."))
        if (recoveryQuestionAnswer.isBlank()) {
            return Result.failure(IllegalArgumentException("Kode/jawaban pemulihan akun wajib diisi."))
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val passHash = hashSecretWithSalt(password, salt)
        val pinHash = hashSecretWithSalt(pin, salt)
        val recoveryHash = hashSecretWithSalt(recoveryQuestionAnswer.trim().lowercase(), salt)

        prefs.edit()
            .putString("username", encryptWithKeystore(username.trim()))
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("password_hash", encryptWithKeystore(passHash))
            .putString("pin_hash", encryptWithKeystore(pinHash))
            .putString("recovery_hash", encryptWithKeystore(recoveryHash))
            .putInt("failed_attempts", 0)
            .putLong("lockout_until", 0L)
            .apply()

        return Result.success(Unit)
    }

    fun isLockedOut(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = prefs.getLong("lockout_until", 0L)
        return nowMillis < until
    }

    fun getRemainingLockoutSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong("lockout_until", 0L)
        return if (nowMillis < until) (until - nowMillis) / 1000L else 0L
    }

    fun verifyPinOrPassword(secretInput: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isLockedOut(nowMillis)) return false
        val saltBase64 = prefs.getString("salt", null) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

        val storedPassHash = prefs.getString("password_hash", null)?.let { decryptWithKeystore(it) } ?: return false
        val storedPinHash = prefs.getString("pin_hash", null)?.let { decryptWithKeystore(it) } ?: return false

        val candidateHash = hashSecretWithSalt(secretInput, salt)
        val matched = constantTimeEquals(candidateHash, storedPassHash) ||
            constantTimeEquals(candidateHash, storedPinHash)

        if (matched) {
            prefs.edit().putInt("failed_attempts", 0).putLong("lockout_until", 0L).apply()
        } else {
            val attempts = prefs.getInt("failed_attempts", 0) + 1
            val editor = prefs.edit().putInt("failed_attempts", attempts)
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                editor.putLong("lockout_until", nowMillis + LOCKOUT_DURATION_MS)
            }
            editor.apply()
        }
        return matched
    }

    fun changePassword(currentSecret: String, newPassword: String): Result<Unit> {
        if (!verifyPinOrPassword(currentSecret)) {
            return Result.failure(SecurityException("Password atau PIN saat ini salah."))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password baru minimal 6 karakter."))
        }
        val salt = Base64.decode(prefs.getString("salt", "") ?: "", Base64.NO_WRAP)
        val newHash = hashSecretWithSalt(newPassword, salt)
        prefs.edit().putString("password_hash", encryptWithKeystore(newHash)).apply()
        return Result.success(Unit)
    }

    fun changePin(currentSecret: String, newPin: String): Result<Unit> {
        if (!verifyPinOrPassword(currentSecret)) {
            return Result.failure(SecurityException("Password atau PIN saat ini salah."))
        }
        if (newPin.length < 6 || !newPin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN baru wajib minimal 6 digit angka."))
        }
        val salt = Base64.decode(prefs.getString("salt", "") ?: "", Base64.NO_WRAP)
        val newHash = hashSecretWithSalt(newPin, salt)
        prefs.edit().putString("pin_hash", encryptWithKeystore(newHash)).apply()
        return Result.success(Unit)
    }

    fun recoverAccountWithAnswer(recoveryAnswer: String, newPin: String): Result<Unit> {
        val saltBase64 = prefs.getString("salt", null)
            ?: return Result.failure(IllegalStateException("Akun belum terdaftar."))
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val storedRecovery = prefs.getString("recovery_hash", null)?.let { decryptWithKeystore(it) }
            ?: return Result.failure(IllegalStateException("Data pemulihan tidak ditemukan."))
        val candidate = hashSecretWithSalt(recoveryAnswer.trim().lowercase(), salt)
        if (!constantTimeEquals(candidate, storedRecovery)) {
            return Result.failure(SecurityException("Jawaban pemulihan tidak sesuai."))
        }
        if (newPin.length < 6 || !newPin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN baru minimal 6 digit angka."))
        }
        val newPinHash = hashSecretWithSalt(newPin, salt)
        prefs.edit()
            .putString("pin_hash", encryptWithKeystore(newPinHash))
            .putInt("failed_attempts", 0)
            .putLong("lockout_until", 0L)
            .apply()
        return Result.success(Unit)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptWithKeystore(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun decryptWithKeystore(cipherTextBase64: String): String {
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val ivSize = combined[0].toInt()
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val encrypted = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherTextBase64
        }
    }
}
