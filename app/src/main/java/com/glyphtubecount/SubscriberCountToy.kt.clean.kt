package com.glyphtubecount

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SubscriberCountToy : Service() {

    private lateinit var mGM: GlyphMatrixManager
    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var youTubeApiService: YouTubeApiService
    private var isRegistered: Boolean = false

    // TODO: Replace with your actual YouTube Data API key
    private val YOUTUBE_API_KEY = "YOUR_API_KEY_GOES_HERE"

    private val mUpdateTask = object : Runnable {
        override fun run() {
            fetchSubscriberCount()
            mHandler.postDelayed(this, 60000) // Update every 60 seconds
        }
    }

    private fun registerOnConnected() {
        try {
            // Prefer the documented device constant if available: Glyph.DEVICE_23112
            val glyphClass = try { Class.forName("com.nothing.ketchum.Glyph") } catch (_: Throwable) { null }
            val target = try {
                glyphClass?.getField("DEVICE_23112")?.get(null) as? String ?: packageName
            } catch (_: Throwable) {
                packageName
            }
            isRegistered = mGM.register(target)
        } catch (_: Throwable) {
            isRegistered = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Network client used by our updates
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClientWithAppHeaders())
            .build()
        youTubeApiService = retrofit.create(YouTubeApiService::class.java)
    }
    
    private fun createOkHttpClientWithAppHeaders(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val signature = getAppSignature()
                
                // Log all request details
                android.util.Log.d("GlyphTube", "=== HTTP REQUEST DETAILS ===")
                android.util.Log.d("GlyphTube", "URL: ${originalRequest.url}")
                android.util.Log.d("GlyphTube", "Method: ${originalRequest.method}")
                android.util.Log.d("GlyphTube", "User-Agent: ${originalRequest.header("User-Agent")}")
                android.util.Log.d("GlyphTube", "Adding X-Android-Package: $packageName")
                android.util.Log.d("GlyphTube", "Adding X-Android-Cert: $signature")
                
                val newRequest = originalRequest.newBuilder()
                    .addHeader("X-Android-Package", packageName)
                    .addHeader("X-Android-Cert", signature)
                    .build()
                
                // Log all headers being sent
                android.util.Log.d("GlyphTube", "=== ALL REQUEST HEADERS ===")
                for (name in newRequest.headers.names()) {
                    android.util.Log.d("GlyphTube", "Header: $name = ${newRequest.header(name)}")
                }
                
                val response = chain.proceed(newRequest)
                
                // Log response details
                android.util.Log.d("GlyphTube", "=== HTTP RESPONSE DETAILS ===")
                android.util.Log.d("GlyphTube", "Response Code: ${response.code}")
                android.util.Log.d("GlyphTube", "Response Message: ${response.message}")
                android.util.Log.d("GlyphTube", "=== RESPONSE HEADERS ===")
                for (name in response.headers.names()) {
                    android.util.Log.d("GlyphTube", "Response Header: $name = ${response.header(name)}")
                }
                
                response
            }
            .build()
    }
    
    private fun getAppSignature(): String {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            
            val signature = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo.apkContentsSigners[0]
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures[0]
            }
            
            val md = java.security.MessageDigest.getInstance("SHA1")
            md.update(signature.toByteArray())
            val digest = md.digest()
            // Google expects SHA-1 fingerprint as uppercase hex string with NO colons or spaces
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("GlyphTube", "Failed to get app signature", e)
            ""
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Per Dev Kit: init() in onBind, register in onServiceConnected
        mGM = GlyphMatrixManager.getInstance(applicationContext)
        val callback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: android.content.ComponentName?) {
                registerOnConnected()
                // Kick off first update once connected
                mHandler.post { fetchSubscriberCount() }
                mHandler.post(mUpdateTask)
            }
            override fun onServiceDisconnected(componentName: android.content.ComponentName?) {
                isRegistered = false
            }
        }
        try {
            mGM.init(callback)
        } catch (_: Throwable) {
            // If init not available in this SDK, try immediate register as fallback
            registerOnConnected()
            mHandler.post { fetchSubscriberCount() }
            mHandler.post(mUpdateTask)
        }
        // Return null is acceptable if you don't need to handle Glyph button events
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mHandler.removeCallbacks(mUpdateTask)
        if (::mGM.isInitialized) {
            try { mGM.unInit() } catch (_: Throwable) {}
        }
        isRegistered = false
        // Return true so that onRebind() will be called next time the toy is selected
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        // Re-initialize and restart updates
        mGM = GlyphMatrixManager.getInstance(applicationContext)
        val callback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: android.content.ComponentName?) {
                registerOnConnected()
                mHandler.post { fetchSubscriberCount() }
                mHandler.post(mUpdateTask)
            }
            override fun onServiceDisconnected(componentName: android.content.ComponentName?) {
                isRegistered = false
            }
        }
        try {
            mGM.init(callback)
        } catch (_: Throwable) {
            registerOnConnected()
            mHandler.post { fetchSubscriberCount() }
            mHandler.post(mUpdateTask)
        }
    }

    private fun fetchSubscriberCount() {
        val settings = getSharedPreferences(MainActivity.PREFS_NAME, 0)
        val channelId = settings.getString(MainActivity.CHANNEL_ID_KEY, null)

        if (channelId.isNullOrEmpty()) {
            displayCountOnGlyphMatrix("No URL")
            return
        }

        // Comprehensive logging for debugging
        android.util.Log.d("GlyphTube", "=== API CALL DEBUG START ===")
        android.util.Log.d("GlyphTube", "Channel ID: $channelId")
        android.util.Log.d("GlyphTube", "API Key: ${YOUTUBE_API_KEY.take(8)}...${YOUTUBE_API_KEY.takeLast(4)}")
        android.util.Log.d("GlyphTube", "API Key Length: ${YOUTUBE_API_KEY.length}")
        android.util.Log.d("GlyphTube", "Package Name: $packageName")
        android.util.Log.d("GlyphTube", "App Signature: ${getAppSignature()}")
        
        // Network connectivity will be handled by Retrofit's failure callback
        
        // Validate API key format
        val isValidApiKeyFormat = YOUTUBE_API_KEY.matches(Regex("^AIza[0-9A-Za-z\\-_]{35}$"))
        android.util.Log.d("GlyphTube", "API Key Format Valid: $isValidApiKeyFormat")
        
        // Log the full URL being called
        val fullUrl = "https://www.googleapis.com/youtube/v3/channels?part=statistics&id=$channelId&key=$YOUTUBE_API_KEY"
        android.util.Log.d("GlyphTube", "Full API URL: ${fullUrl.replace(YOUTUBE_API_KEY, "[API_KEY_HIDDEN]")}")

        youTubeApiService.getChannelDetails("statistics", channelId, YOUTUBE_API_KEY)
            .enqueue(object : Callback<YouTubeChannelResponse> {
                override fun onResponse(call: Call<YouTubeChannelResponse>, response: Response<YouTubeChannelResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        android.util.Log.d("GlyphTube", "API Response successful. Items count: ${body.items.size}")
                        
                        if (body.items.isNotEmpty()) {
                            val subscriberCount = body.items[0].statistics.subscriberCount
                            android.util.Log.d("GlyphTube", "Raw subscriber count from API: '$subscriberCount'")
                            
                            // Ensure we're getting the full count, not a truncated version
                            if (subscriberCount.isNotEmpty()) {
                                // Validate that it's a valid number (no "K", "M" formatting from API)
                                val isValidNumber = subscriberCount.all { it.isDigit() }
                                android.util.Log.d("GlyphTube", "Subscriber count is valid number: $isValidNumber")
                                
                                if (isValidNumber) {
                                    // Convert to long to ensure we can handle large numbers, then back to string
                                    try {
                                        val count = subscriberCount.toLong()
                                        android.util.Log.d("GlyphTube", "Parsed subscriber count: $count")
                                        
                                        // For very large numbers, offer both full and compact display
                                        val displayString = if (count >= 1000000) {
                                            // For millions, show like "1.2M" to fit better
                                            val millions = count / 1000000.0
                                            if (millions >= 10) {
                                                "${(millions.toInt())}M"
                                            } else {
                                                "${String.format("%.1f", millions)}M"
                                            }
                                        } else if (count >= 10000) {
                                            // For 10k+, show like "574K" to fit better
                                            val thousands = count / 1000.0
                                            if (thousands >= 100) {
                                                "${(thousands.toInt())}K"
                                            } else {
                                                "${String.format("%.1f", thousands)}K"
                                            }
                                        } else {
                                            // For smaller numbers, show full count
                                            count.toString()
                                        }
                                        
                                        android.util.Log.d("GlyphTube", "Display string (formatted): '$displayString'")
                                        displayCountOnGlyphMatrix(displayString)
                                    } catch (e: NumberFormatException) {
                                        android.util.Log.e("GlyphTube", "Failed to parse subscriber count: $subscriberCount", e)
                                        displayCountOnGlyphMatrix(subscriberCount) // Fall back to original string
                                    }
                                } else {
                                    android.util.Log.w("GlyphTube", "Subscriber count contains non-digits: $subscriberCount")
                                    displayCountOnGlyphMatrix(subscriberCount) // Display as-is if formatted
                                }
                            } else {
                                android.util.Log.w("GlyphTube", "Subscriber count is empty")
                                displayCountOnGlyphMatrix("No Data")
                            }
                        } else {
                            android.util.Log.w("GlyphTube", "No items in API response")
                            displayCountOnGlyphMatrix("No Data")
                        }
                    } else {
                        android.util.Log.e("GlyphTube", "API Response failed. Code: ${response.code()}, Message: ${response.message()}")
                        if (response.errorBody() != null) {
                            android.util.Log.e("GlyphTube", "Error body: ${response.errorBody()?.string()}")
                        }
                        displayCountOnGlyphMatrix("API Err")
                    }
                }

                override fun onFailure(call: Call<YouTubeChannelResponse>, t: Throwable) {
                    android.util.Log.e("GlyphTube", "API call failed", t)
                    displayCountOnGlyphMatrix("Net Err")
                }
            })
    }

    private fun getIntPref(name: String, def: Int): Int {
        return try { getSharedPreferences(MainActivity.PREFS_NAME, 0).getInt(name, def) } catch (_: Throwable) { def }
    }

    private fun displayCountOnGlyphMatrix(count: String) {
        if (!::mGM.isInitialized || !isRegistered) {
            // If not yet registered, schedule a retry shortly
            mHandler.postDelayed({ displayCountOnGlyphMatrix(count) }, 500)
            return
        }

        // Log the count we're trying to display
        android.util.Log.d("GlyphTube", "Displaying count on matrix: '$count' (${count.length} digits)")
        
        // Basic centering/visibility defaults; can be overridden via prefs
        val defaultScale = 120  // smaller default to prevent clipping
        val defaultX = 4        // center position for most displays
        val defaultY = 8
        val defaultBrightness = 255
        val baseScale = getIntPref("matrix_scale", defaultScale).coerceIn(10, 200)
        val baseX = getIntPref("matrix_pos_x", defaultX).coerceIn(0, 24)
        val posY = getIntPref("matrix_pos_y", defaultY).coerceIn(0, 24)
        val brightness = getIntPref("matrix_brightness", defaultBrightness).coerceIn(0, 255)

        // Auto-fit: much more aggressive scaling for longer numbers
        val digits = count.length
        val scale = when {
            digits <= 3 -> baseScale
            digits == 4 -> (baseScale * 0.80).toInt()
            digits == 5 -> (baseScale * 0.60).toInt()
            digits == 6 -> (baseScale * 0.45).toInt()  // Much smaller for 6 digits
            digits == 7 -> (baseScale * 0.38).toInt()
            else -> (baseScale * 0.32).toInt()
        }.coerceIn(15, 200)
        
        // Smart positioning: center shorter strings, shift left only for very long ones
        val posX = when {
            digits <= 4 -> baseX  // Center 3-4 character strings like "574K"
            digits == 5 -> (baseX - 1).coerceAtLeast(0)
            digits == 6 -> (baseX - 2).coerceAtLeast(0)
            else -> 0  // start at leftmost position for very long numbers
        }
        
        android.util.Log.d("GlyphTube", "Matrix display params: scale=$scale, posX=$posX, posY=$posY")

        val builder = GlyphMatrixObject.Builder()
        val subCountDisplay = builder
            .setText(count)
            .setScale(scale)
            .setBrightness(brightness)
            .setPosition(posX, posY)
            .build()

        val frameBuilder = GlyphMatrixFrame.Builder()
        val frame = frameBuilder.addTop(subCountDisplay).build(this)

        // Per Dev Kit, prefer rendering to raw matrix data
        try {
            val rendered = frame.render()
            mGM.setMatrixFrame(rendered)
        } catch (_: Throwable) {
            // Fallback to direct frame if supported by current SDK
            try { mGM.setMatrixFrame(frame) } catch (_: Throwable) {}
        }
    }
}
