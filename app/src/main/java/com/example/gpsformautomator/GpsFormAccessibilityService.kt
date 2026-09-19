package com.example.gpsformautomator

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class GpsFormAccessibilityService : AccessibilityService() {

    companion object {
        const val FORM_URL =
            "https://docs.google.com/forms/d/e/1FAIpQLScSHU-SGwRzD06BtRkS-WxwS-G6JWrm37CrU2WYC__sAncfKA/viewform?pli=1&pli=1"

        private const val KEY_RUNNING = "running"
        private const val PREFS = "gps_form"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var step = 0
    private var busy = false
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized) prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RUNNING, false)) return
        if (busy) return

        // Only act when a Google Forms/browser window is visible.
        val root = rootInActiveWindow ?: return
        val text = flatten(root).lowercase(Locale.ROOT)
        if (!text.contains("نموذج الاستعلام") && !text.contains("بداية الرحلة") &&
            !text.contains("عدد السائحين") && !text.contains("رقم اللوحة")) return

        busy = true
        handler.postDelayed({
            try {
                advance(rootInActiveWindow ?: return@postDelayed)
            } finally {
                busy = false
            }
        }, 450)
    }

    private fun advance(root: AccessibilityNodeInfo) {
        when (step) {
            0 -> {
                val data = load()
                setByQuestion(root, "بداية الرحلة", data["start"] ?: "")
                setByQuestion(root, "الوجهة", data["destination"] ?: "")
                setByQuestion(root, "اسم الشركة المنفذة؟", data["operatorCompany"] ?: "")
                setByQuestion(root, "رقم ترخيص الشركة المنفذة", data["operatorLicense"] ?: "")
                setByQuestion(root, "اسم الشركة الناقلة؟", data["carrierCompany"] ?: "")
                setByQuestion(root, "رقم ترخيص الشركة الناقلة", data["carrierLicense"] ?: "")
                setByQuestion(root, "نوع المركبة؟", data["vehicleType"] ?: "")
                clickText(root, "التالي")
                step = 1
            }
            1 -> {
                val data = load()
                setByQuestion(root, "عدد السائحين بالأتوبيس؟", data["tourists"] ?: "")
                clickText(root, "التالي")
                step = 2
            }
            2 -> {
                val data = load()
                // Plate number/letters may appear as separate fields. The helper text
                // is used to distinguish the two when both are present.
                setByQuestion(root, "رقم اللوحة", data["plateNumber"] ?: "")
                setByQuestion(root, "الحروف الموجودة باللوحة", data["plateLetters"] ?: "")
                setByQuestion(root, "رقم الشاسية", data["chassis"] ?: "")
                setByQuestion(root, "رقم واتساب مقدم الطلب", data["whatsapp"] ?: "")
                selectNationalities(root, data["nationalities"] ?: "")
                selectRadio(root, data["applicantRole"] ?: "")
                // The form may require one or more intermediate Next buttons depending
                // on conditional vehicle sections. Always take the visible Next first.
                if (!clickText(root, "التالي")) {
                    clickText(root, "إرسال")
                    prefs.edit().putBoolean(KEY_RUNNING, false).apply()
                }
                step = 3
            }
            else -> {
                // Continue through any remaining pages created by conditional logic.
                if (!clickText(root, "التالي")) {
                    if (clickText(root, "إرسال")) {
                        prefs.edit().putBoolean(KEY_RUNNING, false).apply()
                        step = 0
                    }
                }
            }
        }
    }

    private fun load(): Map<String, String> {
        fun g(k: String) = prefs.getString(k, "") ?: ""
        return mapOf(
            "start" to g("start"),
            "destination" to g("destination"),
            "operatorCompany" to g("operatorCompany"),
            "operatorLicense" to g("operatorLicense"),
            "carrierCompany" to g("carrierCompany"),
            "carrierLicense" to g("carrierLicense"),
            "vehicleType" to g("vehicleType"),
            "tourists" to g("tourists"),
            "plateNumber" to g("plateNumber"),
            "plateLetters" to g("plateLetters"),
            "chassis" to g("chassis"),
            "whatsapp" to g("whatsapp"),
            "applicantRole" to g("applicantRole"),
            "nationalities" to g("nationalities")
        )
    }

    private fun all(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        return out
    }

    private fun flatten(root: AccessibilityNodeInfo): String =
        all(root).mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }
            .joinToString(" ")

    private fun normalized(s: String): String =
        s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه').trim()

    private fun findText(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val nt = normalized(target)
        return all(root).firstOrNull {
            val s = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
            normalized(s).contains(nt)
        }
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var n = node
        repeat(7) {
            if (n == null) return null
            if (n.isClickable) return n
            n = n.parent
        }
        return node
    }

    private fun findEditableNear(label: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = label.parent
        repeat(7) {
            if (parent == null) return null
            val edits = ArrayList<AccessibilityNodeInfo>()
            fun walk(n: AccessibilityNodeInfo) {
                if (n.className?.toString()?.contains("EditText") == true || n.isEditable) edits.add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
            }
            walk(parent)
            if (edits.isNotEmpty()) return edits.first()
            parent = parent.parent
        }
        return null
    }

    private fun setByQuestion(root: AccessibilityNodeInfo, question: String, value: String): Boolean {
        if (value.isBlank()) return false
        val label = findText(root, question) ?: return false

        // If the question is a dropdown, clicking the control and selecting the option
        // is handled first.
        val clickable = findClickableAncestor(label)
        val looksDropdown = clickable?.className?.toString()?.contains("Spinner") == true ||
                clickable?.contentDescription?.toString()?.contains("اختيار") == true
        if (looksDropdown) {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                clickText(r, value)
            }, 300)
            return true
        }

        val edit = findEditableNear(label) ?: return false
        edit.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
        )
        return true
    }

    private fun clickText(root: AccessibilityNodeInfo, text: String): Boolean {
        val node = findText(root, text) ?: return false
        return (findClickableAncestor(node) ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun selectNationalities(root: AccessibilityNodeInfo, csv: String) {
        if (csv.isBlank()) return
        val names = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        for (name in names) {
            val node = findText(root, name) ?: continue
            (findClickableAncestor(node) ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun selectRadio(root: AccessibilityNodeInfo, role: String) {
        if (role.isBlank()) return
        val node = findText(root, role) ?: return
        (findClickableAncestor(node) ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() {
        // No-op.
    }
}
