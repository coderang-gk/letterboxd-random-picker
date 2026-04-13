package com.coderang.letterboxdwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = MovieRepository(applicationContext)
        val preferences = repository.getPreferences()

        return runCatching {
            val moviePick = repository.fetchMoviePick(preferences.getFeedUrl())
            if (moviePick.id.isBlank() || moviePick.movie.posterUrl.isBlank()) {
                return Result.failure()
            }

            if (moviePick.id == preferences.getLastAppliedMovieId()) {
                return Result.success()
            }

            val poster = repository.fetchPosterBitmap(moviePick.movie.posterUrl)
            val wallpaperBitmap =
                WallpaperBitmapFormatter(applicationContext).fitPosterToScreen(poster)
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val metrics = applicationContext.resources.displayMetrics
            wallpaperManager.suggestDesiredDimensions(
                metrics.widthPixels,
                metrics.heightPixels,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(
                    wallpaperBitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK,
                )
            } else {
                wallpaperManager.setBitmap(wallpaperBitmap)
            }

            preferences.setLastAppliedMovieId(moviePick.id)
            preferences.setLastAppliedAt(moviePick.generatedAt)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
