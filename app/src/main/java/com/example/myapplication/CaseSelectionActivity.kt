package com.example.myapplication

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class ClinicalCase(
    val id: String,
    val procedure: String,
    val patientName: String,
    val age: Int,
    val gender: String,
    val diagnosis: String,
    val difficulty: Int,
    val objectives: List<String>,
    val skinTone: Int,
    val hairTone: Int,
    val scrubTone: Int,
)

class CaseSelectionActivity : AppCompatActivity() {
    private val cases = listOf(
        ClinicalCase(
            "01", "Stenosi aortica semplice", "Lucia Ferri", 62, "Donna",
            "Valvola aortica severamente calcifica con flusso ridotto. Funzione ventricolare conservata.",
            2, listOf("Mantieni la pressione stabile", "Tempo limite: 8 minuti"),
            0xFFD89D79.toInt(), 0xFF4A3028.toInt(), 0xFF37766A.toInt(),
        ),
        ClinicalCase(
            "02", "Bypass aorto-coronarico multiplo", "Marco Bellini", 58, "Uomo",
            "Coronaropatia trivasale con ischemia inducibile. Programmata rivascolarizzazione miocardica.",
            4, listOf("Completa 3 anastomosi", "Mantieni il flusso coronarico"),
            0xFFC98F70.toInt(), 0xFF3A2B26.toInt(), 0xFF476D81.toInt(),
        ),
        ClinicalCase(
            "03", "Sostituzione valvolare mitralica", "Paolo Ricci", 47, "Uomo",
            "Insufficienza mitralica severa con dilatazione dell'atrio sinistro e dispnea da sforzo.",
            3, listOf("Preserva la funzione ventricolare", "Tempo limite: 10 minuti"),
            0xFFE0AD8B.toInt(), 0xFF51443A.toInt(), 0xFF587C70.toInt(),
        ),
        ClinicalCase(
            "04", "Dissezione aortica urgente", "Sara Moretti", 54, "Donna",
            "Dissezione acuta dell'aorta ascendente. Intervento urgente per prevenire la rottura.",
            5, listOf("Controlla l'emorragia", "Ripristina il flusso entro 12 minuti"),
            0xFFD8A184.toInt(), 0xFF342821.toInt(), 0xFF806044.toInt(),
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        hideSystemUi()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A1A19.toInt())
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val back = MaterialButton(this).apply {
            text = "‹   MENU PRINCIPALE"
            isAllCaps = false
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(0xFFB9D2CC.toInt())
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { finish() }
        }
        content.addView(back, LinearLayout.LayoutParams(-1, dp(42)))

        val kicker = text("ARCHIVIO CLINICO   /   CARDIOCHIRURGIA", 10, 0xFF7EC7B6.toInt(), true)
        kicker.letterSpacing = 0.12f
        content.addView(kicker, matchWrap())
        addSpace(content, 8)
        content.addView(text("Seleziona il caso", 30, Color.WHITE, true), matchWrap())
        addSpace(content, 5)
        content.addView(text("Cartelle aggiornate · ${cases.size} pazienti disponibili", 14, 0xFFB2C8C2.toInt()), matchWrap())
        addSpace(content, 22)

        cases.forEachIndexed { index, case ->
            content.addView(createCaseCard(case), LinearLayout.LayoutParams(-1, -2))
            if (index != cases.lastIndex) addSpace(content, 12)
        }
        addSpace(content, 22)
        content.addView(text("SELEZIONA UNA CARTELLA PER CONSULTARE IL PAZIENTE", 10, 0xFF7EC7B6.toInt(), true), matchWrap())

        setContentView(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun createCaseCard(case: ClinicalCase): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(8).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = 0xFF34534D.toInt()
            setCardBackgroundColor(0xFF172C2A.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { showCaseDetails(case) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(14), dp(15))
        }
        val initials = TextView(this).apply {
            text = case.patientName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            textSize = 15f
            setTextColor(0xFFE5F1ED.toInt())
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(case.scrubTone)
            }
        }
        row.addView(initials, LinearLayout.LayoutParams(dp(46), dp(46)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(8), 0)
        }
        info.addView(text("CASO ${case.id}   ·   ${case.age} ANNI", 10, 0xFF8ECABB.toInt(), true), matchWrap())
        val name = text(case.procedure, 16, Color.WHITE, true)
        name.maxLines = 2
        info.addView(name, matchWrap())
        addSpace(info, 4)
        info.addView(text("${case.patientName}  ·  ${case.gender}", 12, 0xFFB9CBC6.toInt()), matchWrap())
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

        val arrow = text("›", 28, 0xFF8ECABB.toInt(), false).apply { gravity = Gravity.CENTER }
        row.addView(arrow, LinearLayout.LayoutParams(dp(22), dp(46)))
        card.addView(row)
        return card
    }

    private fun showCaseDetails(case: ClinicalCase) {
        startActivity(Intent(this, CaseDetailActivity::class.java).putExtra("case_id", case.id))
    }

    private fun addSection(parent: LinearLayout, heading: String, body: String) {
        addSpace(parent, 18)
        parent.addView(text(heading, 11, 0xFF287A68.toInt(), true), matchWrap())
        addSpace(parent, 5)
        parent.addView(text(body, 14, 0xFF203A35.toInt()), matchWrap())
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
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

class ScalpelRating(context: android.content.Context, private val rating: Int, private val large: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = "Difficoltà: $rating su 5 bisturi"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width / 5f
        val centerY = height / 2f
        for (index in 0 until 5) {
            val centerX = cellWidth * (index + 0.5f)
            paint.color = if (index < rating) 0xFF34A58A.toInt() else 0xFFB9C8C3.toInt()
            paint.strokeWidth = if (large) 3f else 2f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(centerX - 5f, centerY + 5f, centerX + 4f, centerY - 4f, paint)
            canvas.drawLine(centerX - 7f, centerY + 7f, centerX - 4f, centerY + 4f, paint)
            paint.strokeWidth = 1.5f
            canvas.drawLine(centerX + 3f, centerY - 5f, centerX + 6f, centerY - 2f, paint)
        }
    }
}

class PatientPortrait(context: android.content.Context, private val patient: ClinicalCase) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    init {
        contentDescription = "Ritratto illustrato di ${patient.patientName}"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        bounds.set(0f, 0f, width, height)
        paint.shader = LinearGradient(0f, 0f, width, height, 0xFFDFE8E0.toInt(), 0xFF96B7A9.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        paint.shader = null

        paint.color = patient.scrubTone
        val shoulders = Path().apply {
            moveTo(width * 0.13f, height)
            lineTo(width * 0.2f, height * 0.78f)
            lineTo(width * 0.39f, height * 0.68f)
            lineTo(width * 0.61f, height * 0.68f)
            lineTo(width * 0.8f, height * 0.78f)
            lineTo(width * 0.87f, height)
            close()
        }
        canvas.drawPath(shoulders, paint)

        paint.color = patient.skinTone
        canvas.drawRoundRect(width * 0.43f, height * 0.58f, width * 0.57f, height * 0.76f, 8f, 8f, paint)
        canvas.drawOval(width * 0.28f, height * 0.19f, width * 0.72f, height * 0.66f, paint)

        paint.color = patient.hairTone
        canvas.drawOval(width * 0.27f, height * 0.14f, width * 0.73f, height * 0.45f, paint)
        paint.color = patient.skinTone
        canvas.drawOval(width * 0.32f, height * 0.3f, width * 0.68f, height * 0.66f, paint)
        paint.color = patient.hairTone
        canvas.drawOval(width * 0.29f, height * 0.23f, width * 0.39f, height * 0.43f, paint)
        canvas.drawOval(width * 0.61f, height * 0.23f, width * 0.71f, height * 0.43f, paint)
        paint.color = 0xFF35423D.toInt()
        canvas.drawCircle(width * 0.43f, height * 0.46f, 1.5f, paint)
        canvas.drawCircle(width * 0.57f, height * 0.46f, 1.5f, paint)
        paint.color = 0xFF9E6559.toInt()
        paint.strokeWidth = 1.5f
        canvas.drawLine(width * 0.46f, height * 0.56f, width * 0.54f, height * 0.56f, paint)
    }
}