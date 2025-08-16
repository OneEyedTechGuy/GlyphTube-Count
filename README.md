# GlyphTube-Count
Glyph Toy for Nothing Phone 3 to show YouTube Channel Sub Count

<img width="189" height="420" alt="GlyphTube Count screenshot" src="https://github.com/user-attachments/assets/49b34024-cfce-4783-8a3d-6c3e5748167d" />

To Enable it:
* Install this apk
* Launch app and enter the URL to your YT channel
* Go to phone Settings - Glyph Interface - tap on Glyph Toys
* Top right corner click on the sorting icon (next to gear)
* Add Subscriber Count and hit back. 
* Click on Subscriber Count and see Matrix


If you want to clone this project just a few things to update:
In app/build.gradle you have to update
signingConfigs {
        release {
            storeFile file('../my-new-release-key.keystore')
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias 'glyphtube-new-key'
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing true
            enableV2Signing true
        }
    }

Next, app/src/main/java/com/glyphtubecount/SubscriberCountToy.kt.clean.kt rename to SubscriberCountToy.kt
Then add your own API Key (on line 25) 
private val YOUTUBE_API_KEY = "YOUR_API_KEY_GOES_HERE"
