package com.example.gpsformautomator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("gps_form", MODE_PRIVATE)
    }

    private fun field(id: Int): EditText =
        findViewById(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        loadSaved()
        updateStatus()

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            requestBatteryExemption()
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {

            saveAll()

            prefs.edit()
                .putBoolean("running", true)
                .apply()

            updateStatus()

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        GpsFormAccessibilityService.FORM_URL
                    )
                )
            )
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {

            prefs.edit()
                .putBoolean("running", false)
                .apply()

            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()

        updateStatus()
    }

    private fun updateStatus() {

        val accessibilityEnabled =
            try {

                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                    .orEmpty()
                    .contains(
                        packageName,
                        ignoreCase = true
                    )

            } catch (_: Exception) {
                false
            }

        val powerManager =
            getSystemService(POWER_SERVICE)
                    as PowerManager

        val batteryExempt =
            try {
                powerManager.isIgnoringBatteryOptimizations(
                    packageName
                )
            } catch (_: Exception) {
                false
            }

        val running =
            prefs.getBoolean("running", false)

        findViewById<TextView>(R.id.tvStatus).text =
            "الحالة: ${
                if (running) "التنفيذ مفعّل"
                else "متوقف"
            }\n" +
            "إمكانية الوصول: ${
                if (accessibilityEnabled) "مفعّلة"
                else "غير مفعّلة"
            }\n" +
            "البطارية: ${
                if (batteryExempt) "بدون تقييد"
                else "تحتاج استثناء"
            }"
    }

    private fun requestBatteryExemption() {

        try {

            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse(
                        "package:$packageName"
                    )
                )
            )

        } catch (_: Exception) {

            startActivity(
                Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                )
            )
        }
    }

    private fun loadSaved() {

        mapOf(

            R.id.start to "start",

            R.id.destination to "destination",

            R.id.operatorCompany to
                    "operatorCompany",

            R.id.operatorLicense to
                    "operatorLicense",

            R.id.carrierCompany to
                    "carrierCompany",

            R.id.carrierLicense to
                    "carrierLicense",

            R.id.vehicleType to
                    "vehicleType",

            R.id.tourists to
                    "tourists",

            R.id.plateNumber to
                    "plateNumber",

            R.id.plateLetters to
                    "plateLetters",

            R.id.chassis to
                    "chassis",

            R.id.whatsapp to
                    "whatsapp",

            R.id.applicantRole to
                    "applicantRole",

            R.id.nationalities to
                    "nationalities"

        ).forEach { (viewId, key) ->

            field(viewId).setText(
                prefs.getString(key, "") ?: ""
            )
        }
    }

    private fun saveAll() {

        val editor = prefs.edit()

        mapOf(

            R.id.start to "start",

            R.id.destination to "destination",

            R.id.operatorCompany to
                    "operatorCompany",

            R.id.operatorLicense to
                    "operatorLicense",

            R.id.carrierCompany to
                    "carrierCompany",

            R.id.carrierLicense to
                    "carrierLicense",

            R.id.vehicleType to
                    "vehicleType",

            R.id.tourists to
                    "tourists",

            R.id.plateNumber to
                    "plateNumber",

            R.id.plateLetters to
                    "plateLetters",

            R.id.chassis to
                    "chassis",

            R.id.whatsapp to
                    "whatsapp",

            R.id.applicantRole to
                    "applicantRole",

            R.id.nationalities to
                    "nationalities"

        ).forEach { (viewId, key) ->

            editor.putString(
                key,
                field(viewId)
                    .text
                    .toString()
                    .trim()
            )
        }

        editor.apply()
    }
}
