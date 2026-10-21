package com.example.myapplication

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.androidgamesdk.GameActivity
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private val preferences by lazy { getSharedPreferences("surgery_preferences", MODE_PRIVATE) }
    private var lastScoreView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        hideSystemUi()

        val root = android.widget.FrameLayout(this)
        root.addView(OperatingRoomBackdrop(this))
        val salaImage = android.widget.ImageView(this).apply {
            setImageDrawable(loadAssetDrawable("sala.jpg"))
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            alpha = 0.22f
        }
        root.addView(salaImage, android.widget.FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(22), dp(24), dp(22), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))

        val eyebrow = label("UNITÀ CHIRURGICA  /  SIMULATORE", 11, 0xFF8BC9BE.toInt(), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
        }
        content.addView(eyebrow, matchWrap())
        addSpace(content, 12)
        val title = label("PULSE\nROOM", 44, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.04f
            setLineSpacing(-dp(5).toFloat(), 0.92f)
        }
        content.addView(title, matchWrap())
        addSpace(content, 8)
        content.addView(label("Ogni battito conta.", 15, 0xFFD0E4DF.toInt(), false).apply {
            gravity = Gravity.CENTER
        }, matchWrap())
        val lastScore = label("", 12, 0xFF71F0C0.toInt(), true).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(lastScore, matchWrap())
        lastScoreView = lastScore
        addSpace(content, 30)

        addMenuButton(content, "▶   CARRIERA STRUMENTISTA", "Casi clinici · richieste e strumenti", true) {
            startActivity(Intent(this, CaseSelectionActivity::class.java))
        }
        addSpace(content, 10)
        addMenuButton(content, "⚡   URGENZA / QUICK PLAY", "Un'emergenza. Decisioni immediate.", false) {
            openGame()
        }
        addSpace(content, 10)
        addMenuButton(content, "⚙   IMPOSTAZIONI", "Audio, lingua e comandi touch", false) {
            showSettings()
        }
        addSpace(content, 10)
        addMenuButton(content, "🏆   PROFILO / STATISTICHE", "Interventi, brevetti e titoli", false) {
            showProfile()
        }
        addSpace(content, 22)
            val footer = label("MONITORAGGIO VITALE  ·  RITMO REGOLARE", 10, 0xFF8BC9BE.toInt(), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        content.addView(footer, matchWrap())

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val scoreFile = filesDir.resolve("last_turn_score.dat")
        val score = scoreFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (score != null) {
            lastScoreView?.apply {
                text = "ULTIMO INTERVENTO  ·  $score PUNTI"
                visibility = View.VISIBLE
            }
            scoreFile.delete()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun loadAssetDrawable(fileName: String): android.graphics.drawable.Drawable? {
        return runCatching {
            assets.open(fileName).use { stream ->
                android.graphics.drawable.Drawable.createFromStream(stream, null)
            }
        }.getOrNull()
    }

    private fun openGame() {
        startActivity(Intent(this, SurgeryGameActivity::class.java))
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        addToggle(panel, "Musica", "music_enabled", true)
        addToggle(panel, "Effetti sonori e beep", "sound_enabled", true)

        val language = TextView(this).apply {
            text = "Lingua: ${preferences.getString("language", "Italiano")}   ›"
            textSize = 16f
            setTextColor(0xFF173633.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                val languages = arrayOf("Italiano", "English")
                val selected = if (preferences.getString("language", "Italiano") == "English") 1 else 0
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Lingua")
                    .setSingleChoiceItems(languages, selected) { dialog, which ->
                        preferences.edit().putString("language", languages[which]).apply()
                        text = "Lingua: ${languages[which]}   ›"
                        dialog.dismiss()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }
        panel.addView(language, matchWrap())

        val sensitivityLabel = TextView(this).apply {
            text = "Sensibilità touch: ${preferences.getInt("touch_sensitivity", 50)}%"
            textSize = 14f
            setTextColor(0xFF173633.toInt())
        }
        panel.addView(sensitivityLabel, matchWrap())
        val sensitivity = SeekBar(this).apply {
            max = 100
            progress = preferences.getInt("touch_sensitivity", 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sensitivityLabel.text = "Sensibilità touch: $progress%"
                    if (fromUser) preferences.edit().putInt("touch_sensitivity", progress).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        panel.addView(sensitivity, matchWrap())
        AlertDialog.Builder(this)
            .setTitle("Impostazioni")
            .setView(panel)
            .setPositiveButton("Fatto", null)
            .show()
    }

    private fun addToggle(parent: LinearLayout, title: String, key: String, default: Boolean) {
        val toggle = CheckBox(this).apply {
            text = title
            isChecked = preferences.getBoolean(key, default)
            setTextColor(0xFF173633.toInt())
            setOnCheckedChangeListener { _, checked -> preferences.edit().putBoolean(key, checked).apply() }
        }
        parent.addView(toggle, matchWrap())
    }

    private fun showProfile() {
        val careerPoints = filesDir.resolve("instrumentist_career.dat")
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toIntOrNull() ?: 0
        val rank = when {
            careerPoints >= 8000 -> "Coordinatore"
            careerPoints >= 4500 -> "Esperto"
            careerPoints >= 1800 -> "Autonomo"
            careerPoints >= 500 -> "Junior"
            else -> "Neodiplomato"
        }
        AlertDialog.Builder(this)
            .setTitle("Profilo / Statistiche")
            .setMessage("Carriera strumentista\nGrado attuale     $rank\nPunti carriera     $careerPoints")
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun addMenuButton(parent: LinearLayout, title: String, subtitle: String, primary: Boolean, action: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = "$title\n$subtitle"
            isAllCaps = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(12), dp(12), dp(12))
            minHeight = dp(if (primary) 76 else 68)
            cornerRadius = dp(7)
            strokeWidth = dp(if (primary) 0 else 1)
            strokeColor = ColorStateList.valueOf(0x665AC7B1)
            backgroundTintList = ColorStateList.valueOf(if (primary) 0xFFE0F5EF.toInt() else 0xD91C3433.toInt())
            setTextColor(if (primary) 0xFF102C29.toInt() else Color.WHITE)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(if (primary) 76 else 68)))
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun hideSystemUi() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private class OperatingRoomBackdrop(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val monitor = RectF()
    private val ecg = Path()
    private val startTime = System.currentTimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        paint.shader = LinearGradient(0f, 0f, width, height, 0xFF071716.toInt(), 0xFF1C3432.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null

        paint.color = 0x183B6861
        canvas.drawRect(0f, height * 0.25f, width, height * 0.57f, paint)
        paint.color = 0x304C6964
        for (index in 0..3) {
            val x = width * (0.12f + index * 0.25f)
            canvas.drawRoundRect(x, height * 0.27f, x + width * 0.17f, height * 0.48f, 18f, 18f, paint)
        }

        val phase = (System.currentTimeMillis() - startTime) / 45f
        val lampX = width * 0.5f + sin(phase / 180f) * width * 0.025f
        paint.color = 0x183DE0C0
        val light = Path().apply {
            moveTo(lampX - width * 0.11f, height * 0.12f)
            lineTo(lampX + width * 0.11f, height * 0.12f)
            lineTo(width * 0.82f, height * 0.58f)
            lineTo(width * 0.18f, height * 0.58f)
            close()
        }
        canvas.drawPath(light, paint)
        paint.color = 0xFFE2F4EC.toInt()
        canvas.drawOval(lampX - width * 0.12f, height * 0.065f, lampX + width * 0.12f, height * 0.145f, paint)
        paint.color = 0xFF8CBDB0.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawOval(lampX - width * 0.15f, height * 0.055f, lampX + width * 0.15f, height * 0.155f, paint)
        paint.style = Paint.Style.FILL

        monitor.set(width * 0.58f, height * 0.32f, width * 0.94f, height * 0.48f)
        paint.color = 0xE90A1718.toInt()
        canvas.drawRoundRect(monitor, 12f, 12f, paint)
        paint.color = 0xFF355B55.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(monitor, 12f, 12f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF71F0C0.toInt()
        paint.textSize = height * 0.018f
        paint.typeface = Typeface.create("sans-serif-mono", Typeface.BOLD)
        canvas.drawText("HR  72 BPM", monitor.left + 14f, monitor.top + 22f, paint)
        drawEcg(canvas, phase)

        paint.color = 0xFF0B1717.toInt()
        canvas.drawRoundRect(width * 0.12f, height * 0.68f, width * 0.88f, height * 0.79f, 28f, 28f, paint)
        paint.color = 0x553ED1B2
        canvas.drawRoundRect(width * 0.18f, height * 0.65f, width * 0.82f, height * 0.73f, 26f, 26f, paint)
        paint.color = 0x203DE0C0
        canvas.drawRect(0f, height * 0.81f, width, height, paint)

        paint.shader = LinearGradient(0f, 0f, 0f, height, Color.TRANSPARENT, 0xC9071111.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        postInvalidateDelayed(40)
    }

    private fun drawEcg(canvas: Canvas, phase: Float) {
        val left = monitor.left + 12f
        val top = monitor.top + 34f
        val chartWidth = monitor.width() - 24f
        val chartHeight = monitor.height() - 44f
        val baseline = top + chartHeight * 0.55f
        val cycle = 100f
        ecg.reset()
        for (step in 0..chartWidth.toInt() step 2) {
            val position = (step + phase % cycle) % cycle
            val amplitude = when {
                position in 42f..49f -> (position - 42f) / 7f * 0.18f
                position in 49f..55f -> (1f - (position - 49f) / 6f) * 0.18f
                position in 57f..61f -> -(position - 57f) / 4f
                position in 61f..65f -> 1f - (position - 61f) / 4f
                position in 65f..71f -> -0.24f * (1f - (position - 65f) / 6f)
                else -> 0f
            }
            val y = baseline - amplitude * chartHeight * 0.75f
            if (step == 0) ecg.moveTo(left + step, y) else ecg.lineTo(left + step, y)
        }
        paint.color = 0xFF71F0C0.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(ecg, paint)
        paint.style = Paint.Style.FILL
    }
}