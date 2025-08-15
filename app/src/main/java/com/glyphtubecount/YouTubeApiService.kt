package com.glyphtubecount

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    @GET("channels")
    fun getChannelDetails(
        @Query("part") part: String,
        @Query("id") channelId: String,
        @Query("key") apiKey: String
    ): Call<YouTubeChannelResponse>
}