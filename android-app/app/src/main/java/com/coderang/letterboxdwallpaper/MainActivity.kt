package com.coderang.letterboxdwallpaper

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.coderang.letterboxdwallpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: MovieRepository
    private lateinit var preferences: WallpaperPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MovieRepository(this)
        preferences = repository.getPreferences()

        WallpaperScheduler.ensureScheduled(this)

        binding.feedUrlInput.setText(preferences.getFeedUrl())
        binding.saveFeedButton.setOnClickListener {
            val url = binding.feedUrlInput.text.toString().trim()
            if (url.isBlank()) {
                toast("Feed URL cannot be empty.")
                return@setOnClickListener
            }

            preferences.setFeedUrl(url)
            toast("Feed URL saved.")
            loadPreview()
        }

        binding.refreshButton.setOnClickListener {
            loadPreview()
        }

        binding.applyWallpaperButton.setOnClickListener {
            WallpaperScheduler.triggerImmediate(this)
            toast("Wallpaper refresh queued.")
        }

        loadPreview()
        renderAppliedState()
    }

    private fun loadPreview() {
        val feedUrl = binding.feedUrlInput.text.toString().trim().ifBlank {
            preferences.getFeedUrl()
        }

        lifecycleScope.launch {
            runCatching {
                binding.statusText.text = getString(R.string.status_loading)
                val moviePick = repository.fetchMoviePick(feedUrl)
                binding.titleText.text = moviePick.movie.displayName
                binding.metaOverlayText.text = buildMetaLine(moviePick)
                binding.scheduleText.text = getString(
                    R.string.schedule_value,
                    moviePick.event.date,
                    moviePick.event.start.dateTime,
                    moviePick.event.start.timeZone,
                )
                binding.synopsisText.text =
                    moviePick.movie.synopsis.ifBlank { getString(R.string.no_synopsis) }
                binding.detailsText.text = buildDetails(moviePick)
                binding.linksText.text = getString(
                    R.string.links_value,
                    moviePick.movie.link,
                    moviePick.movie.posterUrl,
                )

                if (moviePick.movie.posterUrl.isNotBlank()) {
                    val poster = repository.fetchPosterBitmap(moviePick.movie.posterUrl)
                    binding.posterView.setImageBitmap(poster)
                } else {
                    binding.posterView.setImageDrawable(null)
                }

                binding.statusText.text = getString(
                    R.string.status_loaded,
                    moviePick.id.ifBlank { "pending" },
                )
            }.onFailure {
                binding.statusText.text = getString(R.string.status_failed, it.message ?: "Unknown error")
                toast("Failed to load movie feed.")
            }
        }
    }

    private fun renderAppliedState() {
        val lastAppliedId = preferences.getLastAppliedMovieId().ifBlank { "Not applied yet" }
        val lastAppliedAt = preferences.getLastAppliedAt().ifBlank { "Not yet synced" }
        binding.lastAppliedText.text = getString(
            R.string.last_applied_value,
            lastAppliedId,
            lastAppliedAt,
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun buildMetaLine(moviePick: MoviePick): String {
        val parts = buildList {
            moviePick.movie.year?.let { add(it) }
            if (moviePick.movie.directors.isNotEmpty()) {
                add(moviePick.movie.directors.joinToString())
            }
            if (moviePick.movie.genres.isNotEmpty()) {
                add(moviePick.movie.genres.take(3).joinToString(" • "))
            }
        }

        return parts.joinToString("  |  ")
    }

    private fun buildDetails(moviePick: MoviePick): String {
        return buildList {
            if (moviePick.movie.directors.isNotEmpty()) {
                add("Director: ${moviePick.movie.directors.joinToString()}")
            }
            if (moviePick.movie.genres.isNotEmpty()) {
                add("Genres: ${moviePick.movie.genres.joinToString()}")
            }
            add("Watchlist size: ${moviePick.scrapedMovieCount}")
        }.joinToString("\n")
    }
}
