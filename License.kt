package com.vaibhav.jarvis
import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
object License {
private const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEGzrVeKK5n4jNyOJoLHyIdpU4z17JJKmkwKrviNRXc9AsXcoVGozEx77NlrlVoENLTRL9NSoshZBmAR8TyNXkMQ=="
fun deviceId(c: Context): String {
val a = try { Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "x" } catch (e: Exception) { "x" }
val h = MessageDigest.getInstance("SHA-256").digest((a + "|jarvis").toByteArray())
val hex = h.joinToString("") { String.format("%02X", it) }.take(12)
return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12)
}
fun check(dev: String, code: String): Boolean {
return try {
val sig = Base64.decode(code.replace(Regex("\\s"), ""), Base64.URL_SAFE)
val k = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT)))
val s = Signature.getInstance("SHA256withECDSA")
s.initVerify(k)
s.update(dev.toByteArray())
s.verify(sig)
} catch (e: Exception) { false }
}
fun valid(c: Context): Boolean {
val code = c.getSharedPreferences("j", Context.MODE_PRIVATE).getString("lic", "") ?: ""
return code.isNotEmpty() && check(deviceId(c), code)
}
}
