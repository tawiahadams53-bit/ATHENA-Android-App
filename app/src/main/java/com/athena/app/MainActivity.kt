package com.athena.app

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Bundle
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val endpoint =
        "https://athena-programming-lab.tawiahadams53.workers.dev/api/chat"

    private val purple = Color.rgb(22, 119, 255)
    private val bgColor = Color.rgb(8, 11, 18)
    private val panel = Color.rgb(16, 24, 39)

    private lateinit var root: FrameLayout
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var drawer: FrameLayout
    private lateinit var sendButton: TextView
    private lateinit var voiceButton: TextView
    private var voiceMode = false
    private var tts: TextToSpeech? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<JSONObject>()
    private var waiting = false
    private lateinit var markwon: Markwon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }

        markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .build()

        buildInterface()
        addMessage(
            "ATHENA",
            "Hello. What would you like to work on?"
        )
    }

    private fun dp(n: Int) =
        (n * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }


    private fun buildInterface() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 16, 16))
        }

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 16, 16))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val menu = makeText("☰", 25f, Color.WHITE, true)
        menu.setPadding(dp(8), dp(8), dp(18), dp(8))
        menu.setOnClickListener { openDrawer() }


        val newChat = makeText("+", 28f, Color.WHITE, true)
        newChat.gravity = Gravity.CENTER
        newChat.setOnClickListener { newConversation() }

        toolbar.addView(menu)
        toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        toolbar.addView(newChat, LinearLayout.LayoutParams(dp(44), dp(44)))
        main.addView(toolbar)

        val conversation = FrameLayout(this)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(14), dp(8), dp(14), dp(140))
        }

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(messages)

        conversation.addView(
            scroll,
            FrameLayout.LayoutParams(-1, -1)
        )

        val topFade = View(this).apply {
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(16, 16, 16),
                    Color.rgb(16, 16, 16),
                    Color.TRANSPARENT
                )
            )
        }

        conversation.addView(
            topFade,
            FrameLayout.LayoutParams(-1, dp(36), Gravity.TOP)
        )

        main.addView(
            conversation,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(main, FrameLayout.LayoutParams(-1, -1))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(43, 43, 43))
                cornerRadius = dp(27).toFloat()
                setStroke(dp(1), Color.rgb(65, 65, 65))
            }
            elevation = dp(7).toFloat()
        }

        input = EditText(this).apply {
            hint = "Message"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            minLines = 1
            maxLines = 5
            isSingleLine = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = null
        }

        composer.addView(input, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val plus = makeText("+", 27f, Color.WHITE)
        plus.gravity = Gravity.CENTER
        plus.setOnClickListener { showComposerActions() }

        voiceButton = makeText("Voice", 13f, Color.LTGRAY)
        voiceButton.gravity = Gravity.CENTER
        voiceButton.setPadding(dp(10), dp(8), dp(10), dp(8))
        voiceButton.setOnClickListener { toggleVoiceMode() }

        val mic = makeText("🎙", 20f, Color.WHITE)
        mic.gravity = Gravity.CENTER
        mic.setOnClickListener { startSpeechInput() }

        sendButton = makeText("↑", 25f, Color.WHITE, true)
        sendButton.gravity = Gravity.CENTER
        sendButton.background = GradientDrawable().apply {
            setColor(Color.rgb(22, 119, 255))
            shape = GradientDrawable.OVAL
        }
        sendButton.setOnClickListener { sendMessage() }

        controls.addView(plus, LinearLayout.LayoutParams(dp(42), dp(42)))
        controls.addView(voiceButton)
        controls.addView(mic, LinearLayout.LayoutParams(dp(42), dp(42)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(sendButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        composer.addView(controls)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        }

        root.addView(composer, params)
        buildDrawer()
        setContentView(root)
    }


    private fun addMessage(sender: String, message: String) {
        val isUser = sender == "YOU"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(9), 0, dp(5))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                if (isUser) dp(15) else dp(3),
                if (isUser) dp(11) else dp(5),
                if (isUser) dp(15) else dp(3),
                if (isUser) dp(11) else dp(5)
            )
            if (isUser) {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(18, 61, 130))
                    cornerRadius = dp(21).toFloat()
                }
            }
        }

        val messageText = makeText("", 15.5f, Color.WHITE)
        messageText.setTextIsSelectable(true)

        if (isUser) {
            messageText.text = message
        } else {
            markwon.setMarkdown(messageText, message)
        }

        content.addView(messageText)

        row.addView(
            content,
            LinearLayout.LayoutParams(
                0, -2, if (isUser) 0.88f else 0.98f
            )
        )
        messages.addView(row)

        if (!isUser) {
            val copy = makeText("▢  Copy", 13f, Color.LTGRAY)
            copy.setPadding(dp(5), dp(5), dp(5), dp(8))
            copy.setOnClickListener {
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("ATHENA response", message)
                )
                Toast.makeText(this, "Response copied", Toast.LENGTH_SHORT).show()
            }
            messages.addView(copy)
        }

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()

        if (message.isEmpty() || waiting) return

        addMessage("YOU", message)

        history.add(
            JSONObject()
                .put("role", "user")
                .put("content", message)
        )

        input.text.clear()
        input.clearFocus()

        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)

        waiting = true
        sendButton.text = "..."

        val requestHistory = JSONArray()
        history.takeLast(20).forEach {
            requestHistory.put(it)
        }

        executor.execute {
            var reply: String? = null
            var error: String? = null

            try {
                val connection = URL(endpoint).openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val body = JSONObject()
                    .put("messages", requestHistory)

                OutputStreamWriter(connection.outputStream).use {
                    it.write(body.toString())
                }

                val status = connection.responseCode

                val responseText = if (status in 200..299) {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } else {
                    connection.errorStream?.bufferedReader()?.use {
                        it.readText()
                    } ?: "HTTP error $status"
                }

                val response = JSONObject(responseText)

                if (status in 200..299) {
                    reply = response.optString("reply")
                    if (reply.isNullOrBlank()) {
                        error = "ATHENA returned an empty reply."
                    }
                } else {
                    error = response.optString(
                        "error",
                        "Server error $status"
                    )
                }

                connection.disconnect()

            } catch (e: Exception) {
                error = e.message ?: "Connection failed."
            }

            runOnUiThread {
                waiting = false
                sendButton.text = "↑"

                if (!reply.isNullOrBlank()) {
                    addMessage("ATHENA", reply!!)
                    if (voiceMode) tts?.speak(reply!!, TextToSpeech.QUEUE_FLUSH, null, "athena_reply")
                    history.add(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", reply!!)
                    )
                } else {
                    addMessage(
                        "ATHENA",
                        "Connection problem: ${error ?: "Unknown error"}"
                    )
                }

                input.clearFocus()
                keyboard.hideSoftInputFromWindow(input.windowToken, 0)
            }
        }
    }

    private fun newConversation() {
        if (waiting) return

        history.clear()
        messages.removeAllViews()
        closeDrawer()

        addMessage(
            "ATHENA",
            "New conversation started. What would you like to learn?"
        )
    }

    private fun buildDrawer() {
        drawer = FrameLayout(this).apply {
            visibility = View.GONE
        }

        val dim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { closeDrawer() }
        }

        drawer.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(35), dp(18), dp(18))
            setBackgroundColor(panel)
        }

        side.addView(makeText("ATHENA", 24f, Color.WHITE, true))
        side.addView(makeText(
            "YOUR AI WORKSPACE",
            11f,
            purple,
            true
        ).apply {
            setPadding(0, dp(5), 0, dp(25))
        })

        drawerItem(side, "＋   New chat") {
            newConversation()
        }

        side.addView(makeText(
            "RECENT CONVERSATIONS",
            11f,
            Color.LTGRAY,
            true
        ).apply {
            setPadding(0, dp(25), 0, dp(12))
        })

        side.addView(makeText(
            "No saved conversations yet",
            14f,
            Color.LTGRAY
        ))

        drawerItem(side, "☎   Contact ATHENA") {
            closeDrawer()
            showContactOptions()
        }

        drawerItem(side, "⚙   Settings") {
            closeDrawer()
        }

        drawer.addView(
            side,
            FrameLayout.LayoutParams(dp(300), -1, Gravity.START)
        )

        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showContactOptions() {
        val choices = arrayOf(
            "WhatsApp",
            "Email: adamstawiah29@gmail.com",
            "Email: tawiahadams53@gmail.com"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("Contact ATHENA")
            .setItems(choices) { _, which ->
                val intent = when (which) {
                    0 -> Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/233549830320")
                    )
                    1 -> Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:adamstawiah29@gmail.com")
                    )
                    else -> Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:tawiahadams53@gmail.com")
                    )
                }

                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "No compatible app found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun drawerItem(
        side: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val item = makeText(label, 16f, Color.WHITE)
        item.setPadding(dp(8), dp(14), dp(8), dp(14))
        item.setOnClickListener { action() }
        side.addView(item)
    }

    private fun openDrawer() {
        if (waiting) return
        drawer.visibility = View.VISIBLE
        val side = drawer.getChildAt(1)
        side.translationX = -dp(300).toFloat()
        side.animate().translationX(0f).setDuration(220).start()
    }


    private fun showComposerActions() {
        val options = arrayOf("New conversation", "Contact ATHENA")
        android.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                if (which == 0) newConversation()
                if (which == 1) showContactOptions()
            }
            .show()
    }

    private fun toggleVoiceMode() {
        voiceMode = !voiceMode
        voiceButton.text = if (voiceMode) "Voice ON" else "Voice"
        if (!voiceMode) tts?.stop()
    }

    private fun startSpeechInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 502)
            return
        }
        launchSpeechInput()
    }

    private fun launchSpeechInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to ATHENA")
            }
            startActivityForResult(intent, 501)
        } catch (e: Exception) {
            Toast.makeText(
                this, "Speech recognition unavailable", Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 502 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchSpeechInput()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 501 && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            if (!spoken.isNullOrBlank()) input.setText(spoken)
        }
    }

    private fun closeDrawer() {
        drawer.visibility = View.GONE
    }

    override fun onBackPressed() {
        if (::drawer.isInitialized && drawer.visibility == View.VISIBLE) {
            closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}
