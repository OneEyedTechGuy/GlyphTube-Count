package com.glyphtubecount

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.content.SharedPreferences
import android.widget.Toast
import android.content.Intent
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var channelUrlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var instructionsTextView: TextView

    companion object {
        const val PREFS_NAME = "MyGlyphTubeCountPrefs"
        const val CHANNEL_ID_KEY = "channelId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        channelUrlEditText = findViewById(R.id.channel_url_edittext)
        saveButton = findViewById(R.id.save_button)
        statusTextView = findViewById(R.id.status_text)
        instructionsTextView = findViewById(R.id.instructions_text)

        loadSavedChannelId()

        saveButton.setOnClickListener {
            saveChannelId()
        }
    }

    private fun loadSavedChannelId() {
        val settings = getSharedPreferences(PREFS_NAME, 0)
        val savedChannelId = settings.getString(CHANNEL_ID_KEY, "")
        if (!savedChannelId.isNullOrEmpty()) {
            statusTextView.text = "Currently monitoring Channel ID: $savedChannelId"
            // Show instructions when a channel is already configured
            instructionsTextView.visibility = View.VISIBLE
        }
    }

    private fun saveChannelId() {
        val urlOrUsername = channelUrlEditText.text.toString()
        if (urlOrUsername.isEmpty()) {
            Toast.makeText(this, "Please enter a valid URL or @username.", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            val channelId = getChannelId(urlOrUsername)
            runOnUiThread {
                if (channelId != null) {
                    val settings = getSharedPreferences(PREFS_NAME, 0)
                    val editor = settings.edit()
                    editor.putString(CHANNEL_ID_KEY, channelId)
                    editor.apply()

                    statusTextView.text = "Channel ID saved: $channelId"
                    Toast.makeText(this, "Channel saved successfully!", Toast.LENGTH_SHORT).show()
                    
                    // Show instructions on how to enable the Glyph toy
                    instructionsTextView.visibility = View.VISIBLE

                    // Start the glyph service so updates begin immediately
                    try {
                        startService(Intent(this, SubscriberCountToy::class.java))
                    } catch (t: Throwable) {
                        // Fallback errors are ignored; the Glyphs app can also trigger the service
                    }
                } else {
                    Toast.makeText(this, "Could not find channel ID.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun getChannelId(urlOrUsername: String): String? {
        return try {
            val input = urlOrUsername.trim()

            // 1) Bare channel ID like UCxxxxxxxxxxxxxxxxxxxxxx
            if (input.startsWith("UC") && input.length >= 24) {
                return input
            }

            // 2) Normalize to a proper YouTube URL we can fetch
            val normalizedUrl = when {
                // @handle -> https://www.youtube.com/@handle
                input.startsWith("@") -> "https://www.youtube.com/${input}"

                // Contains youtube.com but may be missing scheme
                input.contains("youtube.com", ignoreCase = true) -> {
                    if (input.startsWith("http://") || input.startsWith("https://")) input
                    else "https://${input}"
                }

                // Nothing we recognize
                else -> null
            }

            // 3) If it's a direct /channel/ URL, extract ID without a network call
            if (normalizedUrl != null && normalizedUrl.contains("/channel/", ignoreCase = true)) {
                val last = normalizedUrl.substringAfterLast('/')
                if (last.startsWith("UC")) return last
            }

            // 4) Fetch the page and look for channelId meta
            if (normalizedUrl != null) {
                val doc = Jsoup.connect(normalizedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(10_000)
                    .followRedirects(true)
                    .get()

                // Primary: Look for channel ID in canonical link
                val canonicalHref = doc.select("link[rel=canonical]").attr("href")
                val match = Regex("/channel/(UC[\\w-]{22,})", RegexOption.IGNORE_CASE).find(canonicalHref)
                if (match != null) return match.groupValues[1]

                // Fallback 1: <meta itemprop="channelId" content="UC..." />
                val meta = doc.select("meta[itemprop=channelId]")
                if (meta.isNotEmpty()) {
                    meta.first()?.attr("content")?.let { cid ->
                        if (cid.startsWith("UC")) return cid
                    }
                }

                // Fallback 2: Search for channel ID in page content/scripts
                val pageContent = doc.html()
                val contentMatch = Regex("\"(UC[a-zA-Z0-9_-]{22})\"")
                    .find(pageContent)
                if (contentMatch != null) {
                    return contentMatch.groupValues[1]
                }
            }

            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}