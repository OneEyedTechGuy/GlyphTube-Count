package com.glyphtubecount

import com.google.gson.annotations.SerializedName

data class YouTubeChannelResponse(
    @SerializedName("items")
    val items: List<Item>
)

data class Item(
    @SerializedName("statistics")
    val statistics: Statistics
)

data class Statistics(
    @SerializedName("subscriberCount")
    val subscriberCount: String
)