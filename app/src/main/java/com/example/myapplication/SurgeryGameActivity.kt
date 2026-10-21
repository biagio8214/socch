package com.example.myapplication

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class SurgicalStep(
    val instruction: String,
    val requiredToolIndex: Int,
    val description: String
)

class SurgeryGameActivity : AppCompatActivity() {
    private val preferences by lazy { getSharedPreferences("surgery_preferences", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    
    private var startTime = 0L
    private var currentStepIndex = 0
    private var score = 1000
    private var mistakes = 0
    private var timerSeconds = 0
    private var isGameOver = false

    private lateinit var titleText: TextView
    private lateinit var instructionText: TextView
    private lateinit var progressText: TextView
    private lateinit var hrText: TextView
    private lateinit var bpText: TextView
    private lateinit var spo2Text: TextView
    private lateinit var timerText: TextView
    private lateinit var scoreText: TextView
    private lateinit var stepImageView: ImageView
    private lateinit var choicesLayout: LinearLayout
    private lateinit var sidebarLayout: LinearLayout
    private var scalpelAnimView: View? = null
    private var activeBloodView: View? = null
    private var activeHandToolView: View? = null

    private val tools = listOf(
        "BISTURI LAMA 21", "BISTURI 11 CORTO", "GARZE", "BISTURI ELETTRICO",
        "CERA OSSO", "SEGA STERNALE", "ASPIRATORE", "DIVARICATORE DVS",
        "PUNTI TI-CRON", "TELINO CEC", "PROLENE 3-0", "TOURNIQUET",
        "CANNULA AORTICA", "CANNULA VENOSA", "SERRA TUBI", "RACCORDO", "CLAMP AORTICO", "PROLENE 4-0", "AGO CARDIOPLEGIA", "FISIOLOGICA FREDDA", "FORBICI DI POTTS", "ASPIRATORE RECOVERY", "PINZE ANATOMICHE", "FORBICI MAYO", "PINZE CALCIO", "MISURATORE VALVOLARE", "PROTESI VALVOLARE", "ETHIBOND CON PLEDGET", "SPINGINODO", "FORBICI DA FILO", "TESTER VALVOLARE", "TUBI CEC", "FILI D'ACCIAIO PER STERNO", "TRONCHESINO", "VICRYL / MONOCRYL", "KOCHER", "FORBICI MESSEBAUER", "KLEMMER", "BISTURI 11 LUNGO", "DIVARICATORE MITRALICO", "DRENAGGI", "VICRYL 2", "VICRYL 1", "VICRYL 3", "MEDICAZIONE STERILE", "SIRINGONE",         "ECOGRAFO", "FILO PACEMAKER", "PINZA BENGOLEA", "SUMP", "VALVOLA", "COLLETTO PROTESI", "CALATA PROTESI", "LEGA NODI", "FINE", "CONTEGGIO AGHI E GARZE"    )
    private val steps = listOf(
        SurgicalStep("Incisione cutanea con Bisturi Lama 21", 0, "Il chirurgo chiede: 'Passami il bisturi lama 21.'"),
        SurgicalStep("Pulizia e tamponamento con Garze", 2, "Il chirurgo chiede: 'Tamponiamo con le garze.'"),
        SurgicalStep("Emostasi con Elettro bisturi", 3, "Il chirurgo chiede: 'Usa l'elettrobisturi per i piccoli vasi.'"),
        SurgicalStep("Sternotomia con Sega", 5, "Il chirurgo chiede: 'Tagliamo lo sterno con la sega.'"),
        SurgicalStep("Pulizia e tamponamento con Garze", 2, "Il chirurgo chiede: 'Tamponiamo il sanguinamento osseo con le garze.'"),
        SurgicalStep("Applicazione Cera d'osso", 4, "Il chirurgo chiede: 'Metti la cera d'osso sui margini ossei.'"),
        SurgicalStep("Aspirazione campo operatorio", 6, "Il chirurgo chiede: 'Aspiriamo i liquidi.'"),
        SurgicalStep("Posizionamento Divaricatore DVS", 7, "Il chirurgo chiede: 'Apriamo con il divaricatore sternale.'"),
        SurgicalStep("Sospensione con Punti Ti-Cron", 8, "Il chirurgo chiede: 'Passami i punti Ti-Cron.'"),
        SurgicalStep("Controllo con Aspiratore", 6, "Il chirurgo chiede: 'Una passata di aspiratore.'"),
        SurgicalStep("Borsa aortica con Prolene 3-0", 10, "Il chirurgo chiede: 'Prepariamo la borsa aortica.'"),
        SurgicalStep("Applicazione Tourniquet aorta", 11, "Il chirurgo chiede: 'I tourniquet sull'aorta.'"),
        SurgicalStep("Pinza Kocher", 35, "Il secondo chirurgo chiede: 'Passami la pinza Kocher.'"),
        SurgicalStep("Forbici Messebauer", 36, "Il chirurgo chiede: 'Passami le forbici Messebauer per tagliare i fili.'"),
        SurgicalStep("Telino CEC", 9, "Il chirurgo chiede: 'Passami il telino CEC.'"),
        SurgicalStep("Pinza Klemmer", 37, "Il chirurgo chiede: 'Passami la pinza Klemmer.'"),
        SurgicalStep("Tubi CEC", 31, "Il chirurgo chiede: 'Colleghiamo i tubi CEC.'"),
        SurgicalStep("Forbici da lavoro", 20, "Il chirurgo chiede: 'Passami le forbici da lavoro.'"),
        SurgicalStep("Serra Tubi", 14, "Il chirurgo chiede: 'Chiudi con il serratubi.'"),
        SurgicalStep("Pinza Klemmer", 37, "Il chirurgo chiede: 'Passami la pinza Klemmer.'"),
        SurgicalStep("Serra Tubi", 14, "Il chirurgo chiede: 'Chiudi con il serratubi.'"),
        SurgicalStep("Pinza Klemmer", 37, "Il chirurgo chiede: 'Passami la pinza Klemmer.'"),
        SurgicalStep("Taglio borsa con Bisturi 11 Corto", 1, "Il chirurgo chiede: 'Incisione con bisturi 11 corto.'"),
        SurgicalStep("Pinza Bengolea", 46, "Il chirurgo chiede: 'Passami la pinza Bengolea.'"),
        SurgicalStep("Cannulazione Aortica", 12, "Il chirurgo chiede: 'Inseriamo la cannula aortica.'"),
        SurgicalStep("Applicazione Serra Tubi", 14, "Il chirurgo chiede: 'Chiudi i tubi con il serratubi.'"),
        SurgicalStep("Borsa atriale con Prolene 3-0", 10, "Il chirurgo chiede: 'Borsa di tabacco atriale.'"),
        SurgicalStep("Tourniquet vena", 11, "Il chirurgo chiede: 'Tourniquet sulla vena.'"),
        SurgicalStep("Pinza Kocher", 35, "Il secondo chirurgo chiede: 'Passami la pinza Kocher.'"),
        SurgicalStep("Forbici Messebauer", 36, "Il chirurgo chiede: 'Passami le forbici Messebauer.'"),
        SurgicalStep("Taglio borsa atriale con Bisturi 11 Corto", 1, "Il chirurgo chiede: 'Taglio atriale.'"),
        SurgicalStep("Pinza Bengolea", 46, "Il chirurgo chiede: 'Passami la pinza Bengolea.'"),
        SurgicalStep("Cannulazione Venosa", 13, "Il chirurgo chiede: 'Inserimento cannula venosa.'"),
        SurgicalStep("Applicazione Serra Tubi", 14, "Il chirurgo chiede: 'Chiudi i tubi con il serratubi.'"),
        SurgicalStep("Inserimento Raccordo", 15, "Il chirurgo chiede: 'Colleghiamo il raccordo.'"),
        SurgicalStep("Incisione con Bisturi 11 Corto", 1, "Il chirurgo chiede: 'Passami il bisturi 11 corto.'"),
        SurgicalStep("Pinza Bengolea", 46, "Il chirurgo chiede: 'Passami la pinza Bengolea.'"),
        SurgicalStep("Sump", 47, "Il chirurgo chiede: 'Passami il sump per aspirare.'"),
        SurgicalStep("Punto cardioplegia con Prolene 4-0", 17, "Il chirurgo chiede: 'Passami il prolene 4-0 per la cardioplegia antegrada.'"),
        SurgicalStep("Ago da cardioplegia", 18, "Il chirurgo chiede: 'Passami l'ago da cardioplegia.'"),
        SurgicalStep("Riempimento cardioplegia", -1, "Il perfusionista riempie il circuito della cardioplegia."),
        SurgicalStep("Clamp Aortico", 16, "Il chirurgo chiede: 'Clamp aortico, arresto cardiaco.'"),
        SurgicalStep("Infusione fisiologica fredda", 19, "Il chirurgo chiede: 'Infondiamo la fisiologica fredda.'"),
        SurgicalStep("Incisione radice aortica con Bisturi 11 lungo", 38, "Il chirurgo chiede: 'Il bisturi 11 lungo per incidere la radice aortica.'"),
        SurgicalStep("Estensione aortotomia con Forbici di Potts", 20, "Il chirurgo chiede: 'Passami le forbici di Potts.'"),
        SurgicalStep("Aspirazione e recupero", 21, "Il chirurgo chiede: 'Aspiratore recovery.'"),
        SurgicalStep("Esposizione cuspidi con punti Ti-Cron 2-0", 8, "Il chirurgo chiede: 'Passami i punti Ti-Cron 2-0 per la trazione dei cuspidi.'"),
        SurgicalStep("Posizionamento divaricatori valvolari (Ross/Morrow)", 39, "Il chirurgo chiede: 'Passami i divaricatori valvolari.'"),
        SurgicalStep("Posizionamento aspiratore sottile", 6, "Il chirurgo chiede: 'L'aspiratore sottile per il campo.'"),
        SurgicalStep("Presa cuspidi con Pinze anatomiche", 22, "Il chirurgo chiede: 'Passami la pinza per afferrare il cuspide.'"),
        SurgicalStep("Taglio cuspidi nativi con Forbici Mayo", 23, "Il chirurgo chiede: 'Tagliamo il cuspide con le forbici mayo.'"),
        SurgicalStep("Asportazione placche con Pinze Calcio", 24, "Il chirurgo chiede: 'Passami le pinze calcio per le placche calcifiche.'"),
        SurgicalStep("Dissezione calcio residuale con Bisturi 11 lungo", 38, "Il chirurgo chiede: 'Il bisturi 11 lungo per rifinire l'anello.'"),
        SurgicalStep("Lavaggio con soluzione fisiologica", 19, "Il chirurgo chiede: 'Siringa con fisiologica per il lavaggio anti-emboli.'"),
        SurgicalStep("Aspirazione frammenti di calcio", 6, "Il chirurgo chiede: 'Aspiriamo subito sotto.'"),
        SurgicalStep("Misurazione anello con Sizer valvolare", 25, "Il chirurgo chiede: 'Passami i misuratori valvolari (sizer) per l'anello.'"),
        SurgicalStep("Lavaggio e preparazione della protesi valvolare", 50, "Il chirurgo chiede: 'Prepariamo la protesi valvolare.'"),
        SurgicalStep("Punti a U con pledget su anello aortico", 27, "Il chirurgo chiede: 'Passami i punti Ethibond con pledget per l'anello.'"),
        SurgicalStep("Passaggio punti su coletto protesi", 51, "Il chirurgo chiede: 'Passiamo i fili attraverso la cuffia della valvola.'"),
        SurgicalStep("Calata della protesi valvolare (seating)", 52, "Il chirurgo chiede: 'Facciamo scorrere la valvola sull'anello.'"),
        SurgicalStep("Legatura nodi con Spinginodo", 53, "Il chirurgo chiede: 'Passami lo spinginodo.'"),
        SurgicalStep("Taglio fili di sutura", 29, "Il chirurgo chiede: 'Taglia le code dei fili con le forbici.'"),
        SurgicalStep("Controllo mobilitÃ  foglietti con tester", 48, "Il chirurgo chiede: 'Passami il siringone per controllare i foglietti valvolari.'"),
        SurgicalStep("Sutura aortotomia con Prolene 4-0", 17, "Il chirurgo chiede: 'Prolene 4-0 per chiudere l'aorta.'"),
        SurgicalStep("Manovre di de-airing e spurgo aria", 49, "Il chirurgo chiede: 'Passami l'ecografo per le manovre di de-airing.'"),
        SurgicalStep("Rimozione clamp aortico", 16, "Il chirurgo chiede: 'Rimuoviamo il clamp aortico.'"),
        SurgicalStep("Iniezione Protamina", 2, "Il chirurgo chiede: 'Prossima protamina.'"),
        SurgicalStep("Conteggio aghi e garze", 55, "Il chirurgo chiede: 'Facciamo il conteggio di aghi e garze.'"),
        SurgicalStep("Decannulazione e chiusura tourniquet borse", 11, "Il chirurgo chiede: 'Chiudiamo i tourniquet sulle borse.'"),
        SurgicalStep("Emostasi finale con Elettrobisturi", 3, "Il chirurgo chiede: 'Usa l'elettrobisturi per l'emostasi.'"),
        SurgicalStep("Posizionamento filo pacemaker", 45, "Il chirurgo chiede: 'Passami il filo del pacemaker.'"),
        SurgicalStep("Posizionamento drenaggi toracici", 40, "Il chirurgo chiede: 'Passami i drenaggi toracici con la pinza.'"),
        SurgicalStep("Suture forti per chiusura e fissaggio drenaggi", 10, "Il chirurgo chiede: 'Punti forti per fissare i drenaggi.'"),
        SurgicalStep("Sternorrafia con fili d'acciaio", 32, "Il chirurgo chiede: 'Passami i fili d'acciaio e il porta-aghi per chiudere lo sterno.'"),
        SurgicalStep("Chiusura con tronchesino metallico", 33, "Il chirurgo chiede: 'Tronchesino per tagliare i fili d'acciaio.'"),
        SurgicalStep("Chiusura pericardio", 41, "Il chirurgo chiede: 'Vicryl 2 per chiudere il pericardio.'"),
        SurgicalStep("Chiusura fascia", 42, "Il chirurgo chiede: 'Vicryl 1 per chiudere la fascia.'"),
        SurgicalStep("Sotto cute", 41, "Il chirurgo chiede: 'Vicryl 2 per il sottocute.'"),
        SurgicalStep("Chiusura cute", 43, "Il chirurgo chiede: 'Vicryl 3 per la cute.'"),
        SurgicalStep("Pulizia finale e medicazione sterile", 44, "Il chirurgo chiede: 'Garze per pulire il torace e medicazione sterile.'")
    )

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isGameOver) return
            timerSeconds++
            val mins = timerSeconds / 60
            val secs = timerSeconds % 60
            timerText.text = String.format("â± TEMPO  %02d:%02d", mins, secs)

            hrText.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                hrText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()

            if (preferences.getBoolean("sound_enabled", true)) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        hideSystemUi()

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
        startTime = SystemClock.elapsedRealtime()

        val root = android.widget.FrameLayout(this)

        // 1. Full Screen Patient Anatomy Background Image
        stepImageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF051010.toInt())
        }
        root.addView(stepImageView)

        // Dark gradient overlay for readability
        val overlay = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(0x99051010.toInt())
        }
        root.addView(overlay)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.TOP
        }

        // Main horizontal container: Left content + Right gray sidebar menu
        val mainHorizontal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val leftParams = LinearLayout.LayoutParams(0, -1, 1f)
        mainHorizontal.addView(contentLayout, leftParams)

        // Right Sidebar Menu (Azzurro with 3 tool images)
        sidebarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2A6F97.toInt()) // Azzurro background
            setPadding(dp(8), dp(16), dp(8), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val sidebarParams = LinearLayout.LayoutParams(dp(160), -1)
        mainHorizontal.addView(sidebarLayout, sidebarParams)

        val sidebarTitle = text("FERRI", 11, 0xFFCCCCCC.toInt(), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        sidebarLayout.addView(sidebarTitle, matchWrap())
        addSpace(sidebarLayout, 10)

        choicesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        sidebarLayout.addView(choicesLayout, LinearLayout.LayoutParams(-1, -1, 1f))

        root.addView(mainHorizontal, ViewGroup.LayoutParams(-1, -1))

        // 2. Advanced Vitals Monitor Card
        val monitorCard = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(0xEE0D1E1C.toInt())
            strokeWidth = dp(1.5f)
            strokeColor = 0xFF34A58A.toInt()
        }
        val monitorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val vitalsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        hrText = text("â¤ï¸ HR: 72", 13, 0xFF71F0C0.toInt(), true)
        bpText = text("ðŸ©¸ PA: 120/80", 13, 0xFF8ECABB.toInt(), true)
        spo2Text = text("ðŸ« SpO2: 99%", 13, 0xFF34A58A.toInt(), true)

        vitalsRow.addView(hrText, LinearLayout.LayoutParams(0, -2, 1f))
        vitalsRow.addView(bpText, LinearLayout.LayoutParams(0, -2, 1.1f))
        vitalsRow.addView(spo2Text, LinearLayout.LayoutParams(0, -2, 0.9f))
        monitorLayout.addView(vitalsRow, matchWrap())

        addSpace(monitorLayout, 6)

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        timerText = text("â± TEMPO  00:00", 12, 0xFFB9CBC6.toInt(), true)
        scoreText = text("ðŸ† PUNTI  1000", 12, 0xFF71F0C0.toInt(), true)
        infoRow.addView(timerText, LinearLayout.LayoutParams(0, -2, 1f))
        infoRow.addView(scoreText, LinearLayout.LayoutParams(0, -2, 1f))
        monitorLayout.addView(infoRow, matchWrap())

        monitorCard.addView(monitorLayout)
        contentLayout.addView(monitorCard, LinearLayout.LayoutParams(-1, -2))

        // Spacer to push dialogue and choices to bottom
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        contentLayout.addView(spacer)

        // 3. Surgeon Message Card at Bottom (without buttons, choices are in right sidebar)
        val dialogCard = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(0xEE132826.toInt())
            strokeWidth = dp(2f)
            strokeColor = 0xFF34A58A.toInt()
        }
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        progressText = text("FASE 1 / ${steps.size}", 11, 0xFF8ECABB.toInt(), true)
        dialogLayout.addView(progressText, matchWrap())
        addSpace(dialogLayout, 4)

        titleText = text(steps[0].instruction, 17, Color.WHITE, true)
        dialogLayout.addView(titleText, matchWrap())
        addSpace(dialogLayout, 2)

        instructionText = text(steps[0].description, 13, 0xFFD8EBE5.toInt(), false)
        dialogLayout.addView(instructionText, matchWrap())

        dialogCard.addView(dialogLayout)
        contentLayout.addView(dialogCard, LinearLayout.LayoutParams(-1, -2))

        // 4. Scalpel Cut Animation View overlay
        scalpelAnimView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(dp(80), dp(80))
            background = getDrawable(android.R.drawable.ic_menu_edit) // placeholder or custom
            visibility = View.GONE
        }
        root.addView(scalpelAnimView)

        setContentView(root)
        loadAssetImage("sala.jpg")
        handler.postDelayed({
            updateStepUI()
        }, 2000L)
        handler.postDelayed(tickRunnable, 1000L)
    }

    private fun getToolImageName(toolIndex: Int): String {
        return when (toolIndex) {
            0 -> "bisturi lama 21.jpg"
            1 -> "bisturi lama 11corto.jpg"
            2 -> "garza.jpg"
            3 -> "bisturi-elettrico.jpg"
            4 -> "ceraosso.jpg"
            5 -> "sega.jpg"
            6 -> "aspiratore.jpg"
            7 -> "dvs.jpg"
            8 -> "ti-cron.jpg"
            9 -> "telinocec.jpg"
            10 -> "prolene3.jpg"
            11 -> "Tourniquet.jpg"
            12 -> "canula aortica.jpg"
            13 -> "canula venosa.jpg"
            14 -> "serra tubi.jpg"
            15 -> "raccordo.jpg"
            16 -> "clampaortico.jpg"
            17 -> "prolene4.jpg"
            18 -> "agocardiojpg.jpg"
            19 -> "fisio fredda.jpg"
            20 -> "forbicilavoro.jpg"
            21 -> "aspiratorerec.jpg"
            22 -> "pinze anatamiche.jpg"
            23 -> "forbici mayo.jpg"
            24 -> "pinze calcio.jpg"
            25 -> "misuratorijpg.jpg"
            26 -> "images.jpg"
            27 -> "aghimitrajpg.jpg"
            28 -> "portaghi.jpg"
            29 -> "formicimez.jpg"
            30 -> "fisio fredda.jpg"
            31 -> "tubi cec.jpg"
            32 -> "acciaiojpg.jpg"
            33 -> "tronchese.jpg"
            34 -> "prolene3.jpg"
            35 -> "kocher.jpg"
            36 -> "formicimez.jpg"
            37 -> "klemmer20.jpg"
            38 -> "bisturi lama 11 lungo.jpg"
            39 -> "divaricatore mitralico.jpg"
            40 -> "drenaggio .jpg"
            41 -> "vicryl2.png"
            42 -> "vicryl1.png"
            43 -> "vicryl3.png"
            44 -> "medicazionejpg.jpg"
            45 -> "pacemakerjpg.jpg"
            46 -> "bengolea.png"
            47 -> "sump.jpg"
            48 -> "siringone.jpg"
            49 -> "eco.jpg"
            50 -> "valvola.jpg"
            51 -> "colletto.jpg"
            52 -> "calata.jpg"
            53 -> "lego.jpg"
            54 -> "fine.jpg"
            55 -> "fine.jpg"
            else -> "images.jpg"
        }
    }

    private fun getStepImageName(): String {
        return when {
            currentStepIndex == 0 -> "step1.jpg"
            currentStepIndex == 1 -> "step2.jpg"
            currentStepIndex == steps.indexOfFirst { it.instruction == "Sump" } -> "sump.jpg"
            currentStepIndex == steps.indexOfFirst { it.instruction == "Lavaggio e preparazione della protesi valvolare" } -> "valvola.jpg"
            currentStepIndex == steps.indexOfFirst { it.instruction == "Passaggio punti su coletto protesi" } -> "colletto.jpg"
            currentStepIndex > steps.indexOfFirst { it.instruction.contains("Pulizia finale e medicazione sterile", ignoreCase = true) } -> "step8.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction.contains("Conteggio aghi e garze", ignoreCase = true) } -> "fine.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction.contains("medicazione", ignoreCase = true) } -> "step8.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction.contains("drenaggi", ignoreCase = true) } -> "step7.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction == "Clamp Aortico" } -> "step6.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction == "Telino CEC" } -> "step5.jpg"
            currentStepIndex >= steps.indexOfFirst { it.instruction == "Posizionamento Divaricatore DVS" } -> "step4.jpg"
            steps.getOrNull(currentStepIndex)?.instruction?.contains("Pulizia e tamponamento con Garze") == true -> "step3.jpg"
            currentStepIndex >= 3 -> "step4.jpg"
            else -> "step1.jpg"
        }
    }

    private fun loadAssetImage(fileName: String) {
        try {
            val inputStream = assets.open(fileName)
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            stepImageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/$fileName")
                val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                stepImageView.setImageDrawable(drawable)
            } catch (e2: Exception) {}
        }

        val isToolImage = fileName != "step1.jpg" && fileName != "step2.jpg" && fileName != "step3.jpg" && fileName != "step4.jpg" && fileName != "step5.jpg" && fileName != "sala.jpg" && fileName != "images.jpg"
        if (isToolImage) {
            val pad = dp(32)
            stepImageView.setPadding(pad, pad, pad, pad)
            stepImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        } else if (fileName in setOf("step1.jpg", "step2.jpg", "step3.jpg", "step4.jpg", "step5.jpg", "step6.jpg", "step7.jpg", "step8.jpg", "sala.jpg")) {
            stepImageView.setPadding(0, 0, 0, 0)
            stepImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            stepImageView.setPadding(0, 0, 0, 0)
            stepImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        stepImageView.alpha = 0f
        stepImageView.animate().alpha(1f).setDuration(400).start()

        if (fileName == "step5.jpg") {
            stepImageView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
        } else if (fileName !in setOf("step1.jpg", "step2.jpg", "step3.jpg", "step4.jpg", "step5.jpg", "step6.jpg", "step7.jpg", "step8.jpg", "sala.jpg") && currentStepIndex >= 6) {
            stepImageView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(400).start()
        } else {
            stepImageView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
        }
    }

    private fun updateStepUI() {
        if (currentStepIndex >= steps.size) return
        val step = steps[currentStepIndex]
        progressText.text = "FASE ${currentStepIndex + 1} / ${steps.size}"
        titleText.text = step.instruction
        instructionText.text = step.description

        if (step.requiredToolIndex < 0) {
            sidebarLayout.visibility = View.GONE
            loadAssetImage(getStepImageName())
            handler.postDelayed({
                if (!isGameOver && currentStepIndex < steps.size && steps[currentStepIndex] == step) {
                    currentStepIndex++
                    if (currentStepIndex >= steps.size) winGame() else updateStepUI()
                }
            }, 2500L)
            return
        }

        sidebarLayout.visibility = View.VISIBLE

        // Always clear blood when showing tool choice screen
        activeBloodView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        activeBloodView = null

        // Keep background anatomy image visible
        loadAssetImage(getStepImageName())

        populateChoices(step.requiredToolIndex)
    }

    private fun loadToolThumbnail(imageView: ImageView, toolIndex: Int) {
        val fileName = getToolImageName(toolIndex)
        try {
            val inputStream = assets.open(fileName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap) // original bitmap with background for sidebar
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/$fileName")
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                imageView.setImageBitmap(bitmap)
            } catch (e2: Exception) {
                imageView.setImageResource(android.R.drawable.ic_menu_edit)
            }
        }
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun removeWhiteBackground(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val width = bmp.width
        val height = bmp.height
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)
            if (r > 200 && g > 200 && b > 200) {
                pixels[i] = android.graphics.Color.TRANSPARENT
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun populateChoices(correctToolIndex: Int) {
        choicesLayout.removeAllViews()

        // Gather 3 choices: 1 correct + 2 random incorrect
        val incorrectIndices = (0 until tools.size).filter { it != correctToolIndex }.shuffled()
        val choices = mutableListOf(correctToolIndex, incorrectIndices[0], incorrectIndices[1]).shuffled()

        choices.forEach { toolIndex ->
            val toolName = tools[toolIndex]
            val card = MaterialCardView(this).apply {
                radius = dp(6).toFloat()
                cardElevation = dp(2).toFloat()
                setCardBackgroundColor(Color.WHITE)
                strokeWidth = dp(1.5f)
                strokeColor = 0xFF2A6F97.toInt()
                setOnClickListener {
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(60).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(60).start()
                    }.start()
                    onChoiceSelected(toolIndex, correctToolIndex)
                }
            }

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }

            val toolImageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(74)) // maximized inside card
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            loadToolThumbnail(toolImageView, toolIndex)
            itemLayout.addView(toolImageView)

            addSpace(itemLayout, 4)

            val nameView = text(toolName, 10, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }
            itemLayout.addView(nameView, matchWrap())

            card.addView(itemLayout)

            val params = LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = dp(6)
            }
            choicesLayout.addView(card, params)
        }
    }

    private fun onChoiceSelected(selected: Int, correct: Int) {
        if (isGameOver) return
        if (selected == correct) {
            sidebarLayout.visibility = View.GONE
            // Gradual recovery from mistakes upon correct action
            if (mistakes > 0) {
                mistakes--
            }

            val hr = 72 - mistakes * 4
            val bpSys = 120 - mistakes * 6
            val bpDia = 80 - mistakes * 4
            val spo2 = 99 - mistakes * 2

            if (mistakes == 0) {
                hrText.text = "â¤ï¸ HR: $hr"
                bpText.text = "ðŸ©¸ PA: $bpSys/$bpDia"
                spo2Text.text = "ðŸ« SpO2: $spo2%"
                hrText.setTextColor(0xFF71F0C0.toInt())
                bpText.setTextColor(0xFF8ECABB.toInt())
                spo2Text.setTextColor(0xFF34A58A.toInt())
            } else {
                hrText.text = "â¤ï¸ HR: $hr [RECUPERO]"
                bpText.text = "ðŸ©¸ PA: $bpSys/$bpDia [STAB]"
                spo2Text.text = "ðŸ« SpO2: $spo2%"
                hrText.setTextColor(0xFFFFD166.toInt())
                bpText.setTextColor(0xFFFFD166.toInt())
                spo2Text.setTextColor(0xFF71F0C0.toInt())
            }

            // Switch to step image upon correct tool selection before playing animation
            loadAssetImage(getStepImageName())

            handler.postDelayed({
                val step = steps[currentStepIndex]
                if (step.instruction.contains("Sternotomia con Sega", ignoreCase = true)) {
                    playSawingAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Legatura nodi", ignoreCase = true) || step.instruction.contains("Calata della protesi", ignoreCase = true)) {
                    playToolActionAnimation(selected) {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Passaggio punti su coletto protesi", ignoreCase = true)) {
                    playVideo("Passaggio punti su coletto protesi.gif") {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Lavaggio e preparazione della protesi valvolare", ignoreCase = true)) {
                    playToolActionAnimation(selected) {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.requiredToolIndex == 26) {
                    playVideo("lavaggio.mp4") {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Calata della protesi", ignoreCase = true)) {
                    playVideo("calovalvola.mp4") {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("punti", ignoreCase = true) || step.instruction.contains("prolene", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Misurazione anello", ignoreCase = true) || step.requiredToolIndex == 25) {
                    playStitchesAnimationCustom("misuratorijpg1.jpg") {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("cardioplegia", ignoreCase = true) || step.instruction.contains("Ago da cardioplegia", ignoreCase = true) || step.instruction.contains("Aspirazione cardioplegia", ignoreCase = true)) {
                    playToolActionAnimation(selected) {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Suture forti", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("pacemaker", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Manovre di de-airing", ignoreCase = true)) {
                    playBackgroundShake(times = 3) {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Conteggio aghi e garze", ignoreCase = true)) {
                    playToolActionAnimation(selected) {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Chiusura con tronchesino", ignoreCase = true) || step.instruction.contains("Sutura aortotomia", ignoreCase = true)) {
                    playVideo("acciaiogif.gif") {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 500L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Chiusura pericardio", ignoreCase = true) || step.instruction.contains("Sotto cute", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Chiusura fascia", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.instruction.contains("Chiusura cute", ignoreCase = true)) {
                    playStitchesAnimation {
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                if (step.requiredToolIndex == 0 || step.requiredToolIndex == 1 || step.instruction.contains("Bisturi", ignoreCase = true)) {
                    playToolActionAnimation(selected) {
                        if (currentStepIndex == 0) {
                            showBlood(nearHeart = false)
                            loadAssetImage("step2.jpg")
                        }
                        handler.postDelayed({
                            currentStepIndex++
                            if (currentStepIndex >= steps.size) winGame()
                            else updateStepUI()
                        }, 1800L)
                    }
                    return@postDelayed
                }

                playToolActionAnimation(selected) {
                    if (currentStepIndex == 0) {
                        showBlood(nearHeart = false)
                        loadAssetImage("step2.jpg")
                    }
                    handler.postDelayed({
                        currentStepIndex++
                        if (currentStepIndex >= steps.size) winGame()
                        else updateStepUI()
                    }, 1800L)
                }
            }, 2000L)
        } else {
            mistakes++
            score = (score - 75).coerceAtLeast(100)
            scoreText.text = "ðŸ† PUNTI  $score"

            playMistakeFlash()

            val hr = 55 - mistakes * 5
            val bpSys = 90 - mistakes * 8
            val bpDia = 60 - mistakes * 5
            val spo2 = (95 - mistakes * 4).coerceAtLeast(60)

            hrText.text = "â¤ï¸ HR: $hr [ALLARME]"
            bpText.text = "ðŸ©¸ PA: $bpSys/$bpDia [CRITICO]"
            spo2Text.text = "ðŸ« SpO2: $spo2% [IPOSSIA]"

            hrText.setTextColor(0xFFFF5252.toInt())
            bpText.setTextColor(0xFFFF5252.toInt())
            spo2Text.setTextColor(0xFFFF5252.toInt())

            hrText.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).withEndAction {
                hrText.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()

            if (preferences.getBoolean("sound_enabled", true)) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 350)
                } catch (e: Exception) {}
            }

            if (mistakes >= 5) {
                gameOver()
            }
        }
    }

    private fun showBlood(nearHeart: Boolean) {
        activeBloodView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val bloodImg = ImageView(this).apply {
            val w = dp(140)
            val h = dp(100)
            layoutParams = android.widget.FrameLayout.LayoutParams(w, h).apply {
                gravity = Gravity.CENTER
                if (nearHeart) {
                    marginStart = -dp(35)
                    topMargin = -dp(12)
                } else {
                    topMargin = dp(20)
                }
            }
            setImageResource(R.drawable.blood_splatter)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.2f
            scaleY = 0.2f
        }

        (stepImageView.parent as? ViewGroup)?.addView(bloodImg)
        activeBloodView = bloodImg

        bloodImg.animate()
            .alpha(0.95f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start()
    }

    private fun playWipingAnimation(onComplete: () -> Unit) {
        val blood = activeBloodView
        if (blood != null) {
            blood.animate()
                .alpha(0f)
                .scaleX(0.1f)
                .scaleY(0.1f)
                .translationYBy(dp(30).toFloat())
                .setDuration(500)
                .withEndAction {
                    (blood.parent as? ViewGroup)?.removeView(blood)
                    activeBloodView = null
                    onComplete()
                }
                .start()
        } else {
            onComplete()
        }
    }

    private fun playSuctionAnimation(onComplete: () -> Unit) {
        val blood = activeBloodView
        if (blood != null) {
            blood.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(150)
                .withEndAction {
                    blood.animate()
                        .alpha(0f)
                        .scaleX(0f)
                        .scaleY(0f)
                        .setDuration(300)
                        .withEndAction {
                            (blood.parent as? ViewGroup)?.removeView(blood)
                            activeBloodView = null
                            onComplete()
                        }
                        .start()
                }
                .start()
        } else {
            onComplete()
        }
    }

    private fun playSawSound() {
        if (!preferences.getBoolean("sound_enabled", true)) return
        try {
            val mediaPlayer = MediaPlayer()
            val afd = assets.openFd("sega.mp3")
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener {
                it.release()
            }
        } catch (e: Exception) {
            try {
                val afd = assets.openFd("surgical_steps/sega.mp3")
                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mediaPlayer.prepare()
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener {
                    it.release()
                }
            } catch (e2: Exception) {}
        }
    }

    private fun playSawingAnimation(onComplete: () -> Unit) {
        playSawSound()
        activeHandToolView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val sawView = SurgicalSawView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(70), dp(200)).apply {
                gravity = Gravity.CENTER
            }
            rotation = 90f
            alpha = 0f
        }

        val parentGroup = (stepImageView.parent as? ViewGroup)
        parentGroup?.addView(sawView)

        sawView.animate().alpha(1f).setDuration(200).withEndAction {
            val animator = ObjectAnimator.ofFloat(sawView, "translationY", -dp(80).toFloat(), dp(80).toFloat(), -dp(80).toFloat(), dp(80).toFloat(), -dp(80).toFloat(), dp(80).toFloat(), -dp(80).toFloat(), dp(80).toFloat(), -dp(80).toFloat(), dp(80).toFloat(), 0f).apply {
                duration = 5000L
            }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sawView.animate().alpha(0f).setDuration(300).withEndAction {
                        parentGroup?.removeView(sawView)
                        onComplete()
                    }.start()
                }
            })
            animator.start()
        }.start()

        // Keep the hand with the saw visible during the complete 5-second movement.
        playHandToolAnimation(toolIndex = 5, onComplete = {})
    }

    private fun playStitchSound() {
        if (!preferences.getBoolean("sound_enabled", true)) return
        val h = Handler(Looper.getMainLooper())
        for (i in 0 until 3) {
            h.postDelayed({
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 50)
                } catch (e: Exception) {}
            }, (i * 150).toLong())
        }
    }

    private fun playStitchesAnimation(onComplete: () -> Unit) {
        playStitchSound()
        val needleImageView = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(70), dp(70)).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }

        try {
            val inputStream = assets.open("ago.png")
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            needleImageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/ago.png")
                val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                needleImageView.setImageDrawable(drawable)
            } catch (e2: Exception) {}
        }

        val parentGroup = (stepImageView.parent as? ViewGroup)
        parentGroup?.addView(needleImageView)

        needleImageView.animate().alpha(1f).scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction {
            needleImageView.animate()
                .translationXBy(dp(80).toFloat())
                .translationYBy(-dp(25).toFloat())
                .rotation(35f)
                .setDuration(800)
                .withEndAction {
                    needleImageView.animate().alpha(0f).setDuration(200).withEndAction {
                        parentGroup?.removeView(needleImageView)
                        onComplete()
                    }.start()
                }.start()
        }.start()
    }

    private fun playReverseStitchesAnimation(onComplete: () -> Unit) {
        playStitchSound()
        val needleImageView = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(70), dp(70)).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }

        try {
            val inputStream = assets.open("ago.png")
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            needleImageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/ago.png")
                val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                needleImageView.setImageDrawable(drawable)
            } catch (e2: Exception) {}
        }

        val parentGroup = (stepImageView.parent as? ViewGroup)
        parentGroup?.addView(needleImageView)

        needleImageView.animate().alpha(1f).scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction {
            needleImageView.animate()
                .translationXBy(-dp(80).toFloat())
                .translationYBy(dp(25).toFloat())
                .rotation(-35f)
                .setDuration(800)
                .withEndAction {
                    needleImageView.animate().alpha(0f).setDuration(200).withEndAction {
                        parentGroup?.removeView(needleImageView)
                        onComplete()
                    }.start()
                }.start()
        }.start()
    }

    private fun playBackgroundShake(times: Int, onComplete: () -> Unit) {
        val cycleDuration = 140L
        var completedCycles = 0
        fun shakeNext() {
            if (completedCycles >= times) {
                stepImageView.translationX = 0f
                onComplete()
                return
            }
            val distance = dp(8).toFloat()
            stepImageView.animate()
                .translationX(distance)
                .setDuration(cycleDuration / 2)
                .withEndAction {
                    stepImageView.animate()
                        .translationX(-distance)
                        .setDuration(cycleDuration)
                        .withEndAction {
                            stepImageView.animate()
                                .translationX(0f)
                                .setDuration(cycleDuration / 2)
                                .withEndAction {
                                    completedCycles++
                                    shakeNext()
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }
        shakeNext()
    }

    private fun playToolActionAnimation(toolIndex: Int, onComplete: () -> Unit) {
        playHandToolAnimation(toolIndex) {
            onComplete()
        }
    }

    private fun playHandToolAnimation(toolIndex: Int, onComplete: () -> Unit) {
        val context = this
        val container = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(140), dp(140)).apply {
                gravity = Gravity.CENTER
            }
            translationX = dp(40).toFloat()
            translationY = dp(80).toFloat()
            alpha = 1f
            scaleX = 0.85f
            scaleY = 0.85f
        }

        // 1. Hand ImageView
        val handImageView = ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        try {
            val inputStream = assets.open("mano.jpg")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            handImageView.setImageBitmap(removeWhiteBackground(bitmap))
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/mano.jpg")
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                handImageView.setImageBitmap(removeWhiteBackground(bitmap))
            } catch (e2: Exception) {
                android.util.Log.e("SurgeryGame", "Unable to load mano.jpg", e2)
            }
        }
        container.addView(handImageView)

        // 2. Selected Tool ImageView overlayed on the hand holding area
        val toolImageView = ImageView(context).apply {
            val isDvs = toolIndex == 7
            val isCecTubes = toolIndex == 31
            layoutParams = android.widget.FrameLayout.LayoutParams(
                when {
                    isDvs -> dp(135)
                    isCecTubes -> dp(145)
                    else -> dp(105)
                },
                when {
                    isDvs -> dp(135)
                    isCecTubes -> dp(145)
                    else -> dp(105)
                }
            ).apply {
                gravity = Gravity.CENTER
                topMargin = if (isDvs) dp(42) else dp(12)
                marginStart = dp(4)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val fileName = if (toolIndex == 47) "sump.jpg" else getToolImageName(toolIndex)
        try {
            val inputStream = assets.open(fileName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            toolImageView.setImageBitmap(removeWhiteBackground(bitmap))
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/$fileName")
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                toolImageView.setImageBitmap(removeWhiteBackground(bitmap))
            } catch (e2: Exception) {
                android.util.Log.e("SurgeryGame", "Unable to load tool asset: $fileName", e2)
            }
        }
        container.addView(toolImageView)

        val parentGroup = (stepImageView.parent as? ViewGroup)
        activeHandToolView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        activeHandToolView = container
        parentGroup?.addView(container, container.layoutParams)

        container.animate()
            .translationX(dp(-20).toFloat())
            .translationY(dp(35).toFloat())
            .scaleX(1f)
            .scaleY(1f)
            .rotation(8f)
            .setDuration(650)
            .withEndAction {
                container.animate().translationX(dp(10).toFloat()).translationY(dp(55).toFloat())
                    .setDuration(450)
                    .withEndAction {
                        container.animate().alpha(0f).setDuration(300).withEndAction {
                            (container.parent as? ViewGroup)?.removeView(container)
                            if (activeHandToolView === container) activeHandToolView = null
                            onComplete()
                        }.start()
                    }.start()
            }.start()
    }

    private fun playCuttingAnimation(onComplete: () -> Unit) {
        val slash = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, dp(6))
            setBackgroundColor(0xFFFF3333.toInt())
            translationY = resources.displayMetrics.heightPixels * 0.35f
            alpha = 0f
        }
        (stepImageView.parent as? ViewGroup)?.addView(slash)

        slash.animate().alpha(1f).setDuration(80).withEndAction {
            slash.animate().translationXBy(resources.displayMetrics.widthPixels.toFloat()).setDuration(220).withEndAction {
                slash.animate().alpha(0f).setDuration(80).withEndAction {
                    (slash.parent as? ViewGroup)?.removeView(slash)
                    onComplete()
                }.start()
            }.start()
        }.start()
    }

    private fun playVideo(fileName: String, onComplete: () -> Unit) {
        if (fileName.endsWith(".gif", ignoreCase = true)) {
            try {
                val gifView = ImageView(this).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val buffer = assets.open(fileName).use { it.readBytes() }
                val movie = android.graphics.Movie.decodeByteArray(buffer, 0, buffer.size)
                if (movie == null) {
                    onComplete()
                    return
                }
                var animationStart = 0L
                val gifDrawable = object : android.graphics.drawable.Drawable(), Runnable {
                    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                    override fun draw(canvas: android.graphics.Canvas) {
                        if (animationStart == 0L) animationStart = android.os.SystemClock.uptimeMillis()
                        val duration = movie.duration().takeIf { it > 0 } ?: 1000
                        movie.setTime(((android.os.SystemClock.uptimeMillis() - animationStart) % duration).toInt())
                        val scale = minOf(
                            bounds.width().toFloat() / movie.width(),
                            bounds.height().toFloat() / movie.height()
                        )
                        val x = bounds.left + (bounds.width() - movie.width() * scale) / 2f
                        val y = bounds.top + (bounds.height() - movie.height() * scale) / 2f
                        canvas.save()
                        canvas.translate(x, y)
                        canvas.scale(scale, scale)
                        movie.draw(canvas, 0f, 0f, paint)
                        canvas.restore()
                        scheduleSelf(this, android.os.SystemClock.uptimeMillis() + 16L)
                    }
                    override fun run() = invalidateSelf()
                    fun stop() { unscheduleSelf(this) }
                    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                    @Deprecated("Deprecated in Java")
                    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
                gifView.setImageDrawable(gifDrawable)
                (stepImageView.parent as? ViewGroup)?.addView(gifView)
                val duration = (movie.duration().takeIf { it > 0 } ?: 1000).toLong()
                handler.postDelayed({
                    gifDrawable.stop()
                    (gifView.parent as? ViewGroup)?.removeView(gifView)
                    onComplete()
                }, duration)
            } catch (e: Exception) {
                onComplete()
            }
            return
        }
        try {
            val cacheFile = java.io.File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                assets.open(fileName).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            val videoView = android.widget.VideoView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)
                setVideoPath(cacheFile.absolutePath)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                }
                setOnCompletionListener {
                    (parent as? ViewGroup)?.removeView(this)
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    (parent as? ViewGroup)?.removeView(this)
                    onComplete()
                    true
                }
            }
            (stepImageView.parent as? ViewGroup)?.addView(videoView)
        } catch (e: Exception) {
            onComplete()
        }
    }

    private fun playStitchesAnimationCustom(imageName: String, onComplete: () -> Unit) {
        playStitchSound()
        val needleImageView = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(90), dp(90)).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }

        try {
            val inputStream = assets.open(imageName)
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            needleImageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            try {
                val inputStream = assets.open("surgical_steps/$imageName")
                val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                needleImageView.setImageDrawable(drawable)
            } catch (e2: Exception) {}
        }

        val parentGroup = (stepImageView.parent as? ViewGroup)
        parentGroup?.addView(needleImageView)

        needleImageView.animate().alpha(1f).scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction {
            needleImageView.animate()
                .translationXBy(dp(80).toFloat())
                .translationYBy(-dp(25).toFloat())
                .rotation(35f)
                .setDuration(800)
                .withEndAction {
                    needleImageView.animate().alpha(0f).setDuration(200).withEndAction {
                        parentGroup?.removeView(needleImageView)
                        onComplete()
                    }.start()
                }.start()
        }.start()
    }

    private fun playHeartCutAnimation(onComplete: () -> Unit) {
        if (preferences.getBoolean("sound_enabled", true)) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_C, 150)
            } catch (e: Exception) {}
        }

        val slash = View(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(50), dp(4)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(10)
            }
            setBackgroundColor(0xFFFF0000.toInt())
            rotation = -30f
            alpha = 0f
        }

        val parentGroup = (stepImageView.parent as? ViewGroup)
        parentGroup?.addView(slash)

        slash.animate().alpha(1f).scaleX(1.5f).setDuration(100).withEndAction {
            slash.animate()
                .translationXBy(dp(30).toFloat())
                .translationYBy(dp(15).toFloat())
                .setDuration(300)
                .withEndAction {
                    slash.animate().alpha(0f).setDuration(100).withEndAction {
                        parentGroup?.removeView(slash)
                        onComplete()
                    }.start()
                }.start()
        }.start()
    }

    private fun playMistakeFlash() {
        val flashView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(0x66FF0000.toInt())
            alpha = 0f
        }
        (stepImageView.parent as? ViewGroup)?.addView(flashView)

        flashView.animate().alpha(1f).setDuration(100).withEndAction {
            flashView.animate().alpha(0f).setDuration(400).withEndAction {
                (flashView.parent as? ViewGroup)?.removeView(flashView)
            }.start()
        }.start()
    }

    private fun winGame() {
        isGameOver = true
        handler.removeCallbacks(tickRunnable)
        
        val careerFile = filesDir.resolve("instrumentist_career.dat")
        val oldPoints = careerFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0
        val earned = score + (300 - timerSeconds.coerceAtMost(250))
        careerFile.writeText((oldPoints + earned).toString())
        filesDir.resolve("last_turn_score.dat").writeText(earned.toString())

        AlertDialog.Builder(this)
            .setTitle("ðŸŽ‰ Intervento Riuscito!")
            .setMessage("Ottimo lavoro, strumentista!\nPaziente stabilizzato con successo.\nPunti guadagnati: $earned")
            .setPositiveButton("Torna al Menu") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun gameOver() {
        isGameOver = true
        handler.removeCallbacks(tickRunnable)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0D1E1C.toInt())
        }

        val imgView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(180))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try {
            val inputStream = assets.open("game over.jpg")
            val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
            imgView.setImageDrawable(drawable)
        } catch (e: Exception) {}
        dialogView.addView(imgView)

        val msgView = TextView(this).apply {
            text = "Troppi errori critici ($mistakes errori).\nPaziente in arresto cardiocircolatorio.\nIntervento fallito."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        dialogView.addView(msgView)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Torna al Menu Iniziale") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private fun hideSystemUi() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

class SurgicalSawView(context: android.content.Context) : View(context) {
    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Handle
        paint.color = 0xFF263238.toInt()
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(0f, h * 0.2f, w * 0.35f, h * 0.8f, 16f, 16f, paint)

        paint.color = 0xFF37474F.toInt()
        canvas.drawRect(w * 0.08f, h * 0.3f, w * 0.14f, h * 0.7f, paint)
        canvas.drawRect(w * 0.18f, h * 0.3f, w * 0.24f, h * 0.7f, paint)

        // Blade
        paint.color = 0xFFECEFF1.toInt()
        canvas.drawRect(w * 0.32f, h * 0.35f, w * 0.98f, h * 0.65f, paint)

        // Saw teeth
        paint.color = 0xFFB0BEC5.toInt()
        val path = android.graphics.Path()
        var startX = w * 0.32f
        val endX = w * 0.98f
        val toothWidth = 14f
        val toothHeight = 10f
        path.moveTo(startX, h * 0.65f)
        while (startX < endX) {
            path.lineTo(startX + toothWidth / 2f, h * 0.65f + toothHeight)
            path.lineTo(startX + toothWidth, h * 0.65f)
            startX += toothWidth
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
