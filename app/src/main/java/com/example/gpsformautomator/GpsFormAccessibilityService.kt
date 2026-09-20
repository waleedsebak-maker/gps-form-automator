package com.example.gpsformautomator

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

class GpsFormAccessibilityService : AccessibilityService() {

    companion object {
        const val FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLScSHU-SGwRzD06BtRkS-WxwS-G6JWrm37CrU2WYC__sAncfKA/viewform"

        private const val PREFS = "gps_form"
        private const val KEY_RUNNING = "running"
    }

    private val handler = Handler(Looper.getMainLooper())

    private var step = 0
    private var busy = false

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        step = 0
        busy = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        }

        if (!prefs.getBoolean(KEY_RUNNING, false)) return
        if (busy) return

        val root = rootInActiveWindow ?: return

        val pageText = flatten(root).lowercase(Locale.ROOT)

        if (
            !pageText.contains("نموذج الاستعلام") &&
            !pageText.contains("بداية الرحلة") &&
            !pageText.contains("عدد السائحين") &&
            !pageText.contains("رقم اللوحة")
        ) {
            return
        }

        busy = true

        handler.postDelayed({
            try {
                processCurrentStep()
            } finally {
                busy = false
            }
        }, 350)
    }

    private fun processCurrentStep() {

        val root = rootInActiveWindow ?: return
        val data = load()

        when (step) {

            0 -> {

                val fields = listOf(
                    "بداية الرحلة" to data["start"].orEmpty(),
                    "الوجهة" to data["destination"].orEmpty(),
                    "اسم الشركة المنفذة؟" to data["operatorCompany"].orEmpty(),
                    "رقم ترخيص الشركة المنفذة" to data["operatorLicense"].orEmpty(),
                    "اسم الشركة الناقلة؟" to data["carrierCompany"].orEmpty(),
                    "رقم ترخيص الشركة الناقلة" to data["carrierLicense"].orEmpty(),
                    "نوع المركبة؟" to data["vehicleType"].orEmpty()
                )

                fillSequentially(
                    root,
                    fields,
                    0
                ) {
                    val r = rootInActiveWindow ?: return@fillSequentially

                    if (clickText(r, "التالي")) {
                        step = 1
                    }
                }
            }

            1 -> {

                val fields = listOf(
                    "عدد السائحين بالأتوبيس؟" to data["tourists"].orEmpty()
                )

                fillSequentially(
                    root,
                    fields,
                    0
                ) {

                    val r = rootInActiveWindow ?: return@fillSequentially

                    if (clickText(r, "التالي")) {
                        step = 2
                    }
                }
            }

            2 -> {

                val fields = listOf(
                    "رقم اللوحة" to data["plateNumber"].orEmpty(),
                    "الحروف الموجودة باللوحة" to data["plateLetters"].orEmpty(),
                    "رقم الشاسية" to data["chassis"].orEmpty(),
                    "رقم واتساب مقدم الطلب" to data["whatsapp"].orEmpty()
                )

                fillSequentially(
                    root,
                    fields,
                    0
                ) {

                    val r = rootInActiveWindow
                        ?: return@fillSequentially

                    selectNationalities(
                        r,
                        data["nationalities"].orEmpty()
                    )

                    handler.postDelayed({

                        val latest = rootInActiveWindow ?: return@postDelayed

                        selectRadio(
                            latest,
                           
