package com.athena.app

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val endpoint =
        "https://athena-programming-lab.tawiahadams53.workers.dev/api/chat"

    private val purple = Color.rgb(124, 77, 255)
    private val background = Color.rgb(18, 12, 35)
    private val panel = Color.rgb(35, 25, 59)

    private lateinit var root: FrameLayout
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var drawer: FrameLayout
    private lateinit var sendButton: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<JSONObject>()
    private var waiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        buildInterface()
        addMessage(
            "ATHENA",
            "Hello. I'm ATHENA, your AI assistant. What would you like to work on?"
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
            setBackgroundColor(background)
        }

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(panel)
        }

        val menu = makeText("☰", 27f, Color.WHITE, true)
        menu.setPadding(dp(8), dp(8), dp(18), dp(8))
        menu.setOnClickListener { openDrawer() }

        val title = makeText("ATHENA", 21f, Color.WHITE, true)
        title.gravity = Gravity.CENTER_VERTICAL
        title.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)

        val newChat = makeText("+", 30f, Color.WHITE, true)
        newChat.gravity = Gravity.CENTER
        newChat.setPadding(dp(12), 0, dp(12), 0)
        newChat.setOnClickListener { newConversation() }

        toolbar.addView(menu)
        toolbar.addView(title)
        toolbar.addView(newChat)
        main.addView(toolbar)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(messages)
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(panel)
        }

        input = EditText(this).apply {
            hint = "Message ATHENA..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.rgb(48, 37, 72))
            minLines = 1
            maxLines = 4
            isSingleLine = false
            showSoftInputOnFocus = true
        }

        sendButton = makeText("➤", 24f, Color.WHITE, true)
        sendButton.gravity = Gravity.CENTER
        sendButton.setPadding(dp(16), dp(10), dp(12), dp(10))
        sendButton.setOnClickListener { sendMessage() }

        inputBar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        inputBar.addView(sendButton)
        main.addView(inputBar)

        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        buildDrawer()
        setContentView(root)
    }

    private fun addMessage(sender: String, message: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (sender == "YOU") Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(
                if (sender == "YOU") Color.rgb(75, 48, 130) else panel
            )
        }

        bubble.addView(makeText(sender, 11f, purple, true))
        bubble.addView(makeText(message, 15f, Color.WHITE))

        row.addView(bubble, LinearLayout.LayoutParams(0, -2, 0.88f))
        messages.addView(row)
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
                sendButton.text = "➤"

                if (!reply.isNullOrBlank()) {
                    addMessage("ATHENA", reply!!)
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

        drawerItem(side, "⚙   Settings") {
            closeDrawer()
        }

        drawer.addView(
            side,
            FrameLayout.LayoutParams(dp(300), -1, Gravity.START)
        )

        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
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
        executor.shutdownNow()
        super.onDestroy()
    }
}
