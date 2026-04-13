package com.coderang.letterboxdwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class MovieRepository(private val context: Context) {
    suspend fun fetchMoviePick(feedUrl: String): MoviePick = withContext(Dispatchers.IO) {
        val json = URL(feedUrl).readTextResponse()
        parseMoviePick(JSONObject(json))
    }

    suspend fun fetchPosterBitmap(url: String): Bitmap = withContext(Dispatchers.IO) {
        val connection = openConnection(url)
        connection.inputStream.use { stream ->
            val bitmap = BitmapFactory.decodeStream(BufferedInputStream(stream))
            checkNotNull(bitmap) { "Failed to decode poster image." }
        }
    }

    fun getPreferences(): WallpaperPreferences = WallpaperPreferences(context)

    private fun parseMoviePick(json: JSONObject): MoviePick {
        val movie = json.getJSONObject("movie")
        val event = json.getJSONObject("event")
        val start = event.getJSONObject("start")
        val end = event.getJSONObject("end")

        return MoviePick(
            id = json.optString("id"),
            generatedAt = json.optString("generatedAt"),
            watchlistUrl = json.optString("watchlistUrl"),
            scrapedMovieCount = json.optInt("scrapedMovieCount"),
            movie = MovieMetadata(
                title = movie.optString("title"),
                year = movie.optString("year").takeIf { it.isNotBlank() },
                displayName = movie.optString("displayName"),
                link = movie.optString("link"),
                synopsis = movie.optString("synopsis"),
                posterUrl = movie.optString("posterUrl"),
                genres = movie.optJSONArray("genres").toStringList(),
                directors = movie.optJSONArray("directors").toStringList(),
            ),
            event = EventMetadata(
                summary = event.optString("summary"),
                description = event.optString("description"),
                date = event.optString("date"),
                start = EventDateTime(
                    dateTime = start.optString("dateTime"),
                    timeZone = start.optString("timeZone"),
                ),
                end = EventDateTime(
                    dateTime = end.optString("dateTime"),
                    timeZone = end.optString("timeZone"),
                ),
            ),
        )
    }

    private fun URL.readTextResponse(): String {
        val connection = openConnection(toString())
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "LetterboxdWallpaper/1.0")
        connection.connect()

        if (connection.responseCode !in 200..299) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            error("Request failed (${connection.responseCode}) for $url $body")
        }

        return connection
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList(length()) {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) {
                add(value)
            }
        }
    }
}
