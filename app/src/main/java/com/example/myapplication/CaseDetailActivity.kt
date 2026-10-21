package com.example.myapplication

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class CaseDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        hideSystemUi()

        val caseId = intent.getStringExtra("case_id") ?: "01"
        val case = getCase(caseId)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A1A19.toInt())
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(22), dp(20), dp(22), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val back = MaterialButton(this).apply {
            text = "‹   INDIETRO"
            isAllCaps = false
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(0xFFB9D2CC.toInt())
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            setOnClickListener { finish() }
        }
        content.addView(back, LinearLayout.LayoutParams(-1, dp(42)))

        val kicker = text("CARTELLA CLINICA   /   CASO ${case.id}", 10, 0xFF7EC7B6.toInt(), true)
        kicker.letterSpacing = 0.12f
        content.addView(kicker, matchWrap())
        addSpace(content, 8)
        content.addView(text(case.procedure, 26, Color.WHITE, true), matchWrap())
        addSpace(content, 18)

        val patientLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF172C2A.toInt())
                cornerRadius = dp(8).toFloat()
            }
        }
        patientLayout.addView(PatientPortrait(this, case), LinearLayout.LayoutParams(dp(84), dp(104)))
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        identity.addView(text(case.patientName, 18, Color.WHITE, true), matchWrap())
        addSpace(identity, 4)
        identity.addView(text("${case.age} anni  ·  ${case.gender}", 13, 0xFFB9CBC6.toInt()), matchWrap())
        addSpace(identity, 8)
        identity.addView(ScalpelRating(this, case.difficulty, true), LinearLayout.LayoutParams(dp(96), dp(24)))
        identity.addView(text("DIFFICOLTÀ ${case.difficulty}/5", 10, 0xFF8ECABB.toInt(), true), matchWrap())
        patientLayout.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(patientLayout, matchWrap())

        addSection(content, "DIAGNOSI", case.diagnosis)
        addSection(content, "OBIETTIVI DELLA MISSIONE", case.objectives.joinToString("\n") { "•  $it" })

        addSpace(content, 30)

        val startButton = MaterialButton(this).apply {
            text = "AVVIA INTERVENTO"
            isAllCaps = false
            textSize = 15f
            setTextColor(0xFF102C29.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF34A58A.toInt())
            cornerRadius = dp(8)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setOnClickListener {
                startActivity(Intent(this@CaseDetailActivity, SurgeryGameActivity::class.java).putExtra("case_id", case.id))
                finish()
            }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, dp(56)))

        setContentView(root)
    }

    private fun getCase(id: String): ClinicalCase {
        val list = listOf(
            ClinicalCase("01", "Stenosi aortica semplice", "Lucia Ferri", 62, "Donna", "Valvola aortica severamente calcifica con flusso ridotto. Funzione ventricolare conservata.", 2, listOf("Mantieni la pressione stabile", "Tempo limite: 8 minuti"), 0xFFD89D79.toInt(), 0xFF4A3028.toInt(), 0xFF37766A.toInt()),
            ClinicalCase("02", "Bypass aorto-coronarico multiplo", "Marco Bellini", 58, "Uomo", "Coronaropatia trivasale con ischemia inducibile. Programmata rivascolarizzazione miocardica.", 4, listOf("Completa 3 anastomosi", "Mantieni il flusso coronarico"), 0xFFC98F70.toInt(), 0xFF3A2B26.toInt(), 0xFF476D81.toInt()),
            ClinicalCase("03", "Sostituzione valvolare mitralica", "Paolo Ricci", 47, "Uomo", "Insufficienza mitralica severa con dilatazione dell'atrio sinistro e dispnea da sforzo.", 3, listOf("Preserva la funzione ventricolare", "Tempo limite: 10 minuti"), 0xFFE0AD8B.toInt(), 0xFF51443A.toInt(), 0xFF587C70.toInt()),
            ClinicalCase("04", "Dissezione aortica urgente", "Sara Moretti", 54, "Donna", "Dissezione acuta dell'aorta ascendente. Intervento urgente per prevenire la rottura.", 5, listOf("Controlla l'emorragia", "Ripristina il flusso entro 12 minuti"), 0xFFD8A184.toInt(), 0xFF342821.toInt(), 0xFF806044.toInt()),
        )
        return list.find { it.id == id } ?: list[0]
    }

    private fun addSection(parent: LinearLayout, heading: String, body: String) {
        addSpace(parent, 20)
        parent.addView(text(heading, 11, 0xFF7EC7B6.toInt(), true), matchWrap())
        addSpace(parent, 6)
        parent.addView(text(body, 14, 0xFFD8EBE5.toInt()), matchWrap())
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
