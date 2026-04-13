package com.coderang.letterboxdwallpaper

data class MoviePick(
    val id: String,
    val generatedAt: String,
    val watchlistUrl: String,
    val scrapedMovieCount: Int,
    val movie: MovieMetadata,
    val event: EventMetadata,
)

data class MovieMetadata(
    val title: String,
    val year: String?,
    val displayName: String,
    val link: String,
    val synopsis: String,
    val posterUrl: String,
    val genres: List<String>,
    val directors: List<String>,
)

data class EventMetadata(
    val summary: String,
    val description: String,
    val date: String,
    val start: EventDateTime,
    val end: EventDateTime,
)

data class EventDateTime(
    val dateTime: String,
    val timeZone: String,
)
