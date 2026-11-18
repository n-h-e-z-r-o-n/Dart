package com.zadhron.adaa.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM authenticated encryption; key never leaves Android Keystore.
 * Atomic file is in noBackupFilesDir, so tokens cannot enter cloud/device backups.
 * Only used on the repository's IO dispatcher.
 */
class SecureSessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "adaa_session_v1"))
    private val alias = "${context.packageName}.session.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized
    fun read(): JSONObject {
        val bytes = try { file.openRead().use { it.readBytes() } }
        catch (_: FileNotFoundException) { return JSONObject() }
        return try {
            require(bytes.size > 13)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        } catch (_: java.security.GeneralSecurityException) {
            file.delete(); JSONObject()
        } catch (_: IllegalArgumentException) {
            file.delete(); JSONObject()
        } catch (_: org.json.JSONException) {
            file.delete(); JSONObject()
        }
    }

    @Synchronized
    fun write(value: JSONObject) {
        if (value.length() == 0) { file.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
