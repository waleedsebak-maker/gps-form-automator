package com.example.gpsformautomator

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gps_form", MODE_PRIVATE) }

    private fun field(id: Int): EditText = findViewById(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadSaved()

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            saveAll()
            prefs.edit().putBoolean("running", true).apply()
            findViewById<TextView>(R.id.tvStatus).text = "الحالة: جارٍ فتح النموذج وبدء التعبئة..."
            val intent = Intent(this, GpsFormAccessibilityService::class.java)
            // The service is already controlled by Android; launching the URL starts the browser.
            startActivity(Intent(Intent.ACTION_VIEW, GpsFormAccessibilityService.FORM_URL.toUri()))
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            prefs.edit().putBoolean("running", false).apply()
            findViewById<TextView>(R.id.tvStatus).text = "الحالة: تم الإيقاف"
        }
    }

    private fun loadSaved() {
        mapOf(
            R.id.start to "start",
            R.id.destination to "destination",
            R.id.operatorCompany to "operatorCompany",
            R.id.operatorLicense to "operatorLicense",
            R.id.carrierCompany to "carrierCompany",
            R.id.carrierLicense to "carrierLicense",
            R.id.vehicleType to "vehicleType",
            R.id.tourists to "tourists",
            R.id.plateNumber to "plateNumber",
            R.id.plateLetters to "plateLetters",
            R.id.chassis to "chassis",
            R.id.whatsapp to "whatsapp",
            R.id.applicantRole to "applicantRole",
            R.id.nationalities to "nationalities"
        ).forEach { (viewId, key) ->
            field(viewId).setText(prefs.getString(key, "") ?: "")
        }
    }

    private fun saveAll() {
        val e = prefs.edit()
        mapOf(
            R.id.start to "start",
            R.id.destination to "destination",
            R.id.operatorCompany to "operatorCompany",
            R.id.operatorLicense to "operatorLicense",
            R.id.carrierCompany to "carrierCompany",
            R.id.carrierLicense to "carrierLicense",
            R.id.vehicleType to "vehicleType",
            R.id.tourists to "tourists",
            R.id.plateNumber to "plateNumber",
            R.id.plateLetters to "plateLetters",
            R.id.chassis to "chassis",
            R.id.whatsapp to "whatsapp",
            R.id.applicantRole to "applicantRole",
            R.id.nationalities to "nationalities"
        ).forEach { (viewId, key) -> e.putString(key, field(viewId).text.toString().trim()) }
        e.apply()
    }

    private fun String.toUri() = android.net.Uri.parse(this)
}
