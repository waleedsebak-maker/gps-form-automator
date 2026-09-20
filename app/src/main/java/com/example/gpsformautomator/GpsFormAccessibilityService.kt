package com.example.gpsformautomator

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class GpsFormAccessibilityService : AccessibilityService() {

    companion object {
        const val FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLScSHU-SGwRzD06BtRkS-WxwS-G6JWrm37CrU2WYC__sAncfKA/viewform"

        private const val PREFS = "gps_form"
        private const val RUNNING = "running"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var step = 0
    private var busy = false

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

        if (!prefs.getBoolean(RUNNING, false) || busy) return

        val root = rootInActiveWindow ?: return
        val text = flatten(root).lowercase(Locale.ROOT)

        if (
            !text.contains("بداية الرحلة") &&
            !text.contains("عدد السائحين") &&
            !text.contains("رقم اللوحة")
        ) return

        busy = true

        handler.postDelayed({
            try {
                process()
            } finally {
                busy = false
            }
        }, 400)
    }

    private fun process() {
        val root = rootInActiveWindow ?: return
        val d = load()

        when (step) {
            0 -> {
                val fields = listOf(
                    "بداية الرحلة" to d("start"),
                    "الوجهة" to d("destination"),
                    "اسم الشركة المنفذة" to d("operatorCompany"),
                    "رقم ترخيص الشركة المنفذة" to d("operatorLicense"),
                    "اسم الشركة الناقلة" to d("carrierCompany"),
                    "رقم ترخيص الشركة الناقلة" to d("carrierLicense"),
                    "نوع المركبة" to d("vehicleType")
                )

                fillFields(root, fields) {
                    val r = rootInActiveWindow ?: return@fillFields
                    if (clickText(r, "التالي")) step = 1
                }
            }

            1 -> {
                val fields = listOf(
                    "عدد السائحين بالأتوبيس" to d("tourists")
                )

                fillFields(root, fields) {
                    val r = rootInActiveWindow ?: return@fillFields
                    if (clickText(r, "التالي")) step = 2
                }
            }

            2 -> {
                val fields = listOf(
                    "رقم اللوحة" to d("plateNumber"),
                    "الحروف الموجودة باللوحة" to d("plateLetters"),
                    "رقم الشاسية" to d("chassis"),
                    "رقم واتساب مقدم الطلب" to d("whatsapp")
                )

                fillFields(root, fields) {
                    val r = rootInActiveWindow ?: return@fillFields

                    selectItems(r, d("nationalities"))

                    handler.postDelayed({
                        val latest = rootInActiveWindow ?: return@postDelayed

                        selectOne(
                            latest,
                            d("applicantRole")
                        )

                        handler.postDelayed({
                            val finalRoot =
                                rootInActiveWindow ?: return@postDelayed

                            if (clickText(finalRoot, "إرسال")) {
                                prefs.edit()
                                    .putBoolean(RUNNING, false)
                                    .apply()
                                step = 0
                            }
                        }, 600)

                    }, 600)
                }
            }

            else -> step = 0
        }
    }

    private fun fillFields(
        root: AccessibilityNodeInfo,
        fields: List<Pair<String, String>>,
        index: Int = 0,
        done: () -> Unit
    ) {
        if (index >= fields.size) {
            done()
            return
        }

        val question = fields[index].first
        val value = fields[index].second

        if (value.isBlank()) {
            fillFields(root, fields, index + 1, done)
            return
        }

        setField(root, question, value) {
            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                fillFields(r, fields, index + 1, done)
            }, 300)
        }
    }

    private fun setField(
        root: AccessibilityNodeInfo,
        question: String,
        value: String,
        done: () -> Unit
    ) {
        val label = findText(root, question)

        if (label == null) {
            done()
            return
        }

        val edit = findEditable(label)

        if (edit != null) {
            edit.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        value
                    )
                }
            )
            done()
            return
        }

        val clickable = clickableParent(label)

        if (clickable == null) {
            done()
            return
        }

        clickable.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )

        handler.postDelayed({
            val r = rootInActiveWindow ?: return@postDelayed

            if (!clickText(r, value)) {
                val option = findText(r, value)
                option?.let {
                    (clickableParent(it) ?: it).performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }
            }

            done()
        }, 500)
    }

    private fun selectItems(
        root: AccessibilityNodeInfo,
        values: String
    ) {
        if (values.isBlank()) return

        values.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { value ->
                findText(root, value)?.let {
                    (clickableParent(it) ?: it).performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }
            }
    }

    private fun selectOne(
        root: AccessibilityNodeInfo,
        value: String
    ) {
        if (value.isBlank()) return

        findText(root, value)?.let {
            (clickableParent(it) ?: it).performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }
    }

    private fun clickText(
        root: AccessibilityNodeInfo,
        text: String
    ): Boolean {
        val node = findText(root, text) ?: return false
        return (clickableParent(node) ?: node).performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )
    }

    private fun findEditable(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var parent = node.parent

        repeat(8) {
            if (parent == null) return null

            val found = all(parent).firstOrNull {
                it.isEditable ||
                    it.className?.toString()
                        ?.contains("EditText", true) == true
            }

            if (found != null) return found
            parent = parent.parent
        }

        return null
    }

    private fun clickableParent(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        var current = node

        repeat(8) {
            if (current == null) return null
            if (current.isClickable) return current
            current = current.parent
        }

        return null
    }

    private fun findText(
        root: AccessibilityNodeInfo,
        target: String
    ): AccessibilityNodeInfo? {
        val wanted = normalize(target)

        return all(root).firstOrNull {
            val value =
                it.text?.toString()
                    ?: it.contentDescription?.toString()
                    ?: ""

            normalize(value).contains(wanted)
        }
    }

    private fun all(
        root: AccessibilityNodeInfo
    ): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()

        fun walk(node: AccessibilityNodeInfo) {
            result.add(node)

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it) }
            }
        }

        walk(root)
        return result
    }

    private fun flatten(
        root: AccessibilityNodeInfo
    ): String {
        return all(root)
            .mapNotNull {
                it.text?.toString()
                    ?: it.contentDescription?.toString()
            }
            .joinToString(" ")
    }

    private fun normalize(value: String): String {
        return value
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace("؟", "")
            .trim()
    }

    private fun d(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    private fun load(): Map<String, String> {
        return mapOf(
            "start" to d("start"),
            "destination" to d("destination"),
            "operatorCompany" to d("operatorCompany"),
            "operatorLicense" to d("operatorLicense"),
            "carrierCompany" to d("carrierCompany"),
            "carrierLicense" to d("carrierLicense"),
            "vehicleType" to d("vehicleType"),
            "tourists" to d("tourists"),
            "plateNumber" to d("plateNumber"),
            "plateLetters" to d("plateLetters"),
            "chassis" to d("chassis"),
            "whatsapp" to d("whatsapp"),
            "applicantRole" to d("applicantRole"),
            "nationalities" to d("nationalities")
        )
    }

    override fun onInterrupt() {
        busy = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
