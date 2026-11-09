package com.aminminer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var poolInput: EditText
    private lateinit var walletInput: EditText
    private lateinit var statusText: TextView
    private var pendingStart = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra("status") ?: "لا توجد حالة متاحة"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("amin_miner", MODE_PRIVATE)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 28)
        }

        fun label(value: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 10)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        fun field(hintText: String, value: String, type: Int): EditText =
            EditText(this).apply {
                hint = hintText
                setText(value)
                inputType = type
                textSize = 15f
                singleLine = true
                setPadding(16, 8, 16, 8)
            }

        content.addView(label("AMIN MINER", 27f, true))
        content.addView(label("تعدين VerusHash • نسخة اختبار", 17f, true))
        content.addView(label(
            "التشغيل يتم بموافقتك فقط. يبدأ التطبيق بخيط معالجة واحد لتقليل الحمل والحرارة.",
            14f
        ))

        content.addView(label("عنوان مجمع التعدين الآمن TLS", 14f, true))
        poolInput = field(
            "stratum+ssl://pool.example:port",
            prefs.getString("pool", "") ?: "",
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        )
        content.addView(poolInput, LinearLayout.LayoutParams(-1, -2))

        content.addView(label("عنوان محفظة VRSC", 14f, true))
        walletInput = field(
            "أدخل عنوان محفظتك",
            prefs.getString("wallet", "") ?: "",
            android.text.InputType.TYPE_CLASS_TEXT
        )
        content.addView(walletInput, LinearLayout.LayoutParams(-1, -2))

        statusText = label(prefs.getString("status", "التعدين متوقف") ?: "التعدين متوقف", 14f)
        content.addView(statusText)

        val startButton = Button(this).apply {
            text = "بدء التعدين"
            setOnClickListener { requestStart() }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, -2))

        val stopButton = Button(this).apply {
            text = "إيقاف التعدين"
            setOnClickListener { stopMining() }
        }
        content.addView(stopButton, LinearLayout.LayoutParams(-1, -2))

        content.addView(label(
            "حماية مبدئية: إيقاف عند حالة حرارة Android الشديدة، أو عند انخفاض البطارية إلى 20% دون شحن. لا توجد أرباح أو سرعة مفترضة؛ ستظهر فقط بيانات المحرك الفعلية عند توفرها.",
            13f
        ))
        content.addView(label(
            "استخدم مجمعًا موثوقًا يدعم stratum+ssl:// فقط. لا تدخل مفاتيح خاصة أو كلمات مرور محفظتك.",
            13f
        ))

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun requestStart() {
        val pool = poolInput.text.toString().trim()
        val wallet = walletInput.text.toString().trim()
        if (!pool.startsWith("stratum+ssl://") || wallet.isBlank()) {
            Toast.makeText(this, "أدخل عنوان مجمع TLS وعنوان المحفظة أولًا", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("amin_miner", MODE_PRIVATE).edit()
            .putString("pool", pool)
            .putString("wallet", wallet)
            .apply()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4103)
            return
        }
        launchMiningService(pool, wallet)
    }

    private fun launchMiningService(pool: String, wallet: String) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(poolInput.windowToken, 0)
        val intent = Intent(this, MiningService::class.java).apply {
            action = MiningService.ACTION_START
            putExtra(MiningService.EXTRA_POOL, pool)
            putExtra(MiningService.EXTRA_WALLET, wallet)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        statusText.text = "جارٍ بدء خدمة التعدين..."
    }

    private fun stopMining() {
        startService(Intent(this, MiningService::class.java).setAction(MiningService.ACTION_STOP))
        statusText.text = "تم طلب إيقاف التعدين"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4103 && pendingStart) {
            pendingStart = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchMiningService(poolInput.text.toString().trim(), walletInput.text.toString().trim())
            } else {
                Toast.makeText(this, "يلزم السماح بالإشعارات لعرض حالة التشغيل الدائمة", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MiningService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(statusReceiver, filter)
        statusText.text = getSharedPreferences("amin_miner", MODE_PRIVATE)
            .getString("status", "التعدين متوقف")
    }

    override fun onStop() {
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) { }
        super.onStop()
    }
}
