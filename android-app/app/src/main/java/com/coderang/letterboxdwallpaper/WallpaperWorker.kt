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

            WallpaperApplier(applicationContext).applyMoviePoster(
                moviePick,
                repository,
                preferences,
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
