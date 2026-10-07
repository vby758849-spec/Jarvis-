package com.vaibhav.jarvis
import android.Manifest.permission.*
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
class MainActivity : Activity() {
 private lateinit var status: TextView
 private val perms = mutableListOf(RECORD_AUDIO, CALL_PHONE, SEND_SMS, READ_CONTACTS).apply {
  if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
 }.toTypedArray()
 private val DEF = "Mera naam Vaibhav hai (majin Vaibhav). Main games bana raha hoon: Ludo, chess, Majin runner, Yadavi Ji Dodh Nikale, torch game, aur Jarvis assistant."
 override fun onCreate(b: Bundle?) {
  super.onCreate(b)
  val sp = getSharedPreferences("j", MODE_PRIVATE)
  if (!sp.contains("prof")) sp.edit().putString("prof", DEF).apply()
  val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
  l.addView(TextView(this).apply {
   text = "JARVIS"; textSize = 36f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#00D4FF"))
  })
  status = TextView(this).apply {
   text = "Band hai"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, 24, 0, 24)
  }
  l.addView(status)
  val key = EditText(this).apply {
   hint = "Gemini API key"; setText(sp.getString("key", "")); setSingleLine(true)
   setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
  }
  val pico = EditText(this).apply {
   hint = "Picovoice AccessKey"; setText(sp.getString("pico", "")); setSingleLine(true)
   setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
  }
  val prof = EditText(this).apply {
   hint = "Mere baare mein"; setText(sp.getString("prof", DEF)); minLines = 4
   inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
   setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
  }
  l.addView(key); l.addView(pico); l.addView(prof)
  l.addView(Button(this).apply {
   text = "Save (key + meri jaankari)"
   setOnClickListener {
    sp.edit().putString("key", key.text.toString().trim()).putString("pico", pico.text.toString().trim()).putString("prof", prof.text.toString()).apply()
    Toast.makeText(this@MainActivity, "Save ho gaya", Toast.LENGTH_SHORT).show()
   }
  })
  l.addView(Button(this).apply { text = "Jarvis ON"; setOnClickListener { ask() } })
  l.addView(Button(this).apply {
   text = "Jarvis OFF"
   setOnClickListener {
    stopService(Intent(this@MainActivity, JarvisService::class.java))
    status.text = "Band hai"
   }
  })
  setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#050B14")); addView(l) })
 }
 private fun ask() {
  if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms, 1)
  else proceed()
 }
 override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
  proceed()
 }
 private fun proceed() {
  if (checkSelfPermission(RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
   Toast.makeText(this, "Mic ki permission zaroori hai", Toast.LENGTH_LONG).show(); return
  }
  if (!Settings.canDrawOverlays(this)) {
   Toast.makeText(this, "Jarvis ko 'Display over other apps' allow karo, phir wapas aakar ON dabao", Toast.LENGTH_LONG).show()
   startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
   return
  }
  startForegroundService(Intent(this, JarvisService::class.java))
  status.text = "Jarvis ON. Ab 'Jarvis' bolo"
 }
}
