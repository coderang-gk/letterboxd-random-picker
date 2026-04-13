package com.coderang.letterboxdwallpaper

import android.content.Context

class WallpaperPreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences("movie_wallpaper_preferences", Context.MODE_PRIVATE)

    fun getFeedUrl(): String = preferences.getString(KEY_FEED_URL, DEFAULT_FEED_URL).orEmpty()

    fun setFeedUrl(feedUrl: String) {
        preferences.edit().putString(KEY_FEED_URL, feedUrl).apply()
    }

    fun getLastAppliedMovieId(): String = preferences.getString(KEY_LAST_APPLIED_ID, "").orEmpty()

    fun setLastAppliedMovieId(id: String) {
        preferences.edit().putString(KEY_LAST_APPLIED_ID, id).apply()
    }

    fun getLastAppliedAt(): String = preferences.getString(KEY_LAST_APPLIED_AT, "").orEmpty()

    fun setLastAppliedAt(value: String) {
        preferences.edit().putString(KEY_LAST_APPLIED_AT, value).apply()
    }

    fun getPreferredCalendarId(): Long = preferences.getLong(KEY_PREFERRED_CALENDAR_ID, -1L)

    fun setPreferredCalendar(id: Long) {
        preferences.edit().putLong(KEY_PREFERRED_CALENDAR_ID, id).apply()
    }

    companion object {
        const val DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/coderang-gk/letterboxd-random-picker/main/public/latest-movie.json"

        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_LAST_APPLIED_ID = "last_applied_id"
        private const val KEY_LAST_APPLIED_AT = "last_applied_at"
        private const val KEY_PREFERRED_CALENDAR_ID = "preferred_calendar_id"
    }
}
