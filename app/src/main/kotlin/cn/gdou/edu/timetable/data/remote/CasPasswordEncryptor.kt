package cn.gdou.edu.timetable.data.remote

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exact implementation of GDOU authserver's encrypt.js:
 * - charset = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
 * - encryptAES(password, salt) = Base64(AES-CBC-PKCS5Padding(randomString(64) + password, key=salt.trim(), iv=randomString(16)))
 */
object CasPasswordEncryptor {
    private const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private val secureRandom = SecureRandom()

    fun encryptPassword(
        plainPassword: String,
        pwdEncryptSalt: String,
        prefix64: String = randomString(64),
        iv16: String = randomString(16)
    ): String {
        val trimmedSalt = pwdEncryptSalt.trim()
        if (trimmedSalt.isEmpty()) return plainPassword

        val keyBytes = trimmedSalt.toByteArray(Charsets.UTF_8)
        val ivBytes = iv16.toByteArray(Charsets.UTF_8)
        val payloadBytes = (prefix64 + plainPassword).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ivBytes)
        )
        val encrypted = cipher.doFinal(payloadBytes)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptForVerification(cipherBase64: String, pwdEncryptSalt: String, iv16: String): String {
        val trimmedSalt = pwdEncryptSalt.trim()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(trimmedSalt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv16.toByteArray(Charsets.UTF_8))
        )
        val plain = String(cipher.doFinal(Base64.getDecoder().decode(cipherBase64)), Charsets.UTF_8)
        return if (plain.length >= 64) plain.substring(64) else plain
    }

    fun randomString(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(AES_CHARS[secureRandom.nextInt(AES_CHARS.length)])
        }
        return sb.toString()
    }
}
