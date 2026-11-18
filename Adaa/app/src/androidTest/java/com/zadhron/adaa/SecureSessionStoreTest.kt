package com.zadhron.adaa

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zadhron.adaa.data.session.SecureSessionStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    @Test fun sessionSurvivesRecreationWithoutPlaintextTokensAndRejectsTampering() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(application.cacheDir, "secure-session-test").apply { mkdirs() }
        val context = object : ContextWrapper(application) {
            override fun getNoBackupFilesDir() = directory
            override fun getPackageName() = "${application.packageName}.storage-test"
        }
        val keyAlias = "${context.packageName}.session.v1"
        val fixture = "test-token-only-never-a-real-credential"
        try {
            SecureSessionStore(context).write(JSONObject().put("session", JSONObject().put("access_token", fixture)))
            val encrypted = File(directory, "adaa_session_v1")
            assertFalse(encrypted.readText(Charsets.ISO_8859_1).contains(fixture))
            assertEquals(fixture, SecureSessionStore(context).read().getJSONObject("session").getString("access_token"))
            val bytes = encrypted.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            encrypted.writeBytes(bytes)
            assertEquals(0, SecureSessionStore(context).read().length())
            assertFalse(encrypted.exists())
        } finally {
            File(directory, "adaa_session_v1").delete()
            directory.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
        }
    }
}
