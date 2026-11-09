package com.aminminer

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 32)
        }

        fun label(text: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }

        content.addView(label("AMIN MINER", 28f, true))
        content.addView(label("اختبار التعدين على الجهاز", 18f, true))
        content.addView(label(
            "الجهاز المستهدف: Infinix X6726B • ذاكرة 6 GB",
            14f
        ))
        content.addView(label(
            "حالة المشروع: تجهيز محرك التعدين والاختبارات",
            16f,
            true
        ))
        content.addView(label(
            "لم يبدأ التعدين. لن يعمل أي تعدين قبل دمج محرك حقيقي، " +
                "وعرض موافقة واضحة، وتفعيل إشعار دائم وحدود الحرارة والبطارية.",
            15f
        ))
        content.addView(label(
            "العملات المرشحة للاختبار: Verus (VRSC) وMonero (XMR). " +
                "سيتم الاختيار بناءً على قياس فعلي، وليس أرباحًا تقديرية.",
            15f
        ))
        content.addView(label(
            "لا توجد حاليًا سرعة تعدين أو أرصدة أو أرباح معروضة؛ " +
                "هذه الشاشة تأسيسية فقط.",
            14f
        ))

        setContentView(ScrollView(this).apply { addView(content) })
    }
}
