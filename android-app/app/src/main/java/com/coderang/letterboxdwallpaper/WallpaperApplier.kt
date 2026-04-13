package com.coderang.letterboxdwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.os.Build

class WallpaperApplier(private val context: Context) {
    suspend fun applyMoviePoster(
        moviePick: MoviePick,
        repository: MovieRepository,
        preferences: WallpaperPreferences,
    ) {
        require(moviePick.movie.posterUrl.isNotBlank()) {
            "Movie does not have a poster URL."
        }

        val poster = repository.fetchPosterBitmap(moviePick.movie.posterUrl)
        val wallpaperBitmap = WallpaperBitmapFormatter(context).fitPosterToScreen(poster)
        val wallpaperManager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        wallpaperManager.suggestDesiredDimensions(
            metrics.widthPixels,
            metrics.heightPixels,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                wallpaperManager.setBitmap(
                    wallpaperBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK,
                )
            }.getOrElse {
                wallpaperManager.setBitmap(wallpaperBitmap)
            }
        } else {
            wallpaperManager.setBitmap(wallpaperBitmap)
        }

        preferences.setLastAppliedMovieId(moviePick.id)
        preferences.setLastAppliedAt(moviePick.generatedAt)
    }
}
