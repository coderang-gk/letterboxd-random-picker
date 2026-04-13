package com.coderang.letterboxdwallpaper

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.coderang.letterboxdwallpaper.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: MovieRepository
    private lateinit var preferences: WallpaperPreferences
    private lateinit var calendarManager: DeviceCalendarManager
    private var currentMoviePick: MoviePick? = null
    private var pendingCalendarMoviePick: MoviePick? = null

    private val calendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = REQUIRED_CALENDAR_PERMISSIONS.all { permission ->
                result[permission] == true
            }
            if (granted) {
                pendingCalendarMoviePick?.let(::addMovieToCalendar)
            } else {
                binding.statusText.text = getString(
                    R.string.status_failed,
                    getString(R.string.calendar_permission_denied),
                )
                toast(getString(R.string.calendar_permission_denied))
            }
            pendingCalendarMoviePick = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        repository = MovieRepository(this)
        preferences = repository.getPreferences()
        calendarManager = DeviceCalendarManager(this)

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
            applyWallpaperNow()
        }
        binding.openStremioButton.setOnClickListener {
            currentMoviePick?.let(::openInStremio) ?: toast("Load a movie first.")
        }
        binding.addToCalendarButton.setOnClickListener {
            val moviePick = currentMoviePick
            if (moviePick == null) {
                toast("Load a movie first.")
                return@setOnClickListener
            }

            ensureCalendarAccessAndAdd(moviePick)
        }

        loadPreview()
        renderAppliedState()
    }

    private fun applySystemInsets() {
        val initialTopPadding = binding.appBarContainer.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = initialTopPadding + systemBars.top)
            insets
        }
    }

    private fun loadPreview() {
        val feedUrl = binding.feedUrlInput.text.toString().trim().ifBlank {
            preferences.getFeedUrl()
        }

        lifecycleScope.launch {
            runCatching {
                binding.statusText.text = getString(R.string.status_loading)
                val moviePick = repository.fetchMoviePick(feedUrl)
                currentMoviePick = moviePick
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

    private fun applyWallpaperNow() {
        val moviePick = currentMoviePick
        if (moviePick == null) {
            toast("Load a movie first.")
            return
        }

        lifecycleScope.launch {
            runCatching {
                binding.statusText.text = getString(R.string.status_applying)
                WallpaperApplier(this@MainActivity).applyMoviePoster(
                    moviePick,
                    repository,
                    preferences,
                )
                renderAppliedState()
                binding.statusText.text = getString(
                    R.string.status_applied,
                    moviePick.movie.displayName,
                )
                toast("Wallpaper applied.")
            }.onFailure {
                binding.statusText.text = getString(
                    R.string.status_failed,
                    it.message ?: "Unknown error",
                )
                toast("Failed to apply wallpaper.")
            }
        }
    }

    private fun ensureCalendarAccessAndAdd(moviePick: MoviePick) {
        if (hasCalendarPermissions()) {
            addMovieToCalendar(moviePick)
            return
        }

        pendingCalendarMoviePick = moviePick
        calendarPermissionLauncher.launch(REQUIRED_CALENDAR_PERMISSIONS)
    }

    private fun hasCalendarPermissions(): Boolean {
        return REQUIRED_CALENDAR_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun addMovieToCalendar(moviePick: MoviePick) {
        binding.statusText.text = getString(R.string.status_adding_calendar)

        val calendars = runCatching { calendarManager.getWritableCalendars() }.getOrElse {
            binding.statusText.text = getString(
                R.string.status_failed,
                it.message ?: getString(R.string.calendar_error_toast),
            )
            toast(getString(R.string.calendar_error_toast))
            return
        }

        if (calendars.isEmpty()) {
            binding.statusText.text = getString(
                R.string.status_failed,
                getString(R.string.calendar_unavailable),
            )
            toast(getString(R.string.calendar_unavailable))
            return
        }

        val savedCalendarId = preferences.getPreferredCalendarId()
        val preferredCalendar = calendars.firstOrNull { it.id == savedCalendarId }

        when {
            preferredCalendar != null -> saveCalendarEvent(moviePick, preferredCalendar)
            calendars.size == 1 -> saveCalendarEvent(moviePick, calendars.first())
            else -> showCalendarPicker(calendars, moviePick)
        }
    }

    private fun showCalendarPicker(calendars: List<DeviceCalendar>, moviePick: MoviePick) {
        val labels = calendars.map { calendar ->
            if (calendar.accountName.isBlank()) {
                calendar.displayName
            } else {
                "${calendar.displayName} (${calendar.accountName})"
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.calendar_pick_required)
            .setItems(labels) { _, which ->
                saveCalendarEvent(moviePick, calendars[which])
            }
            .show()
    }

    private fun saveCalendarEvent(moviePick: MoviePick, calendar: DeviceCalendar) {
        runCatching {
            calendarManager.insertEvent(moviePick, calendar.id)
            preferences.setPreferredCalendar(calendar.id)
            binding.statusText.text = getString(
                R.string.status_calendar_added,
                moviePick.movie.displayName,
            )
            toast(getString(R.string.calendar_saved_toast))
        }.onFailure {
            binding.statusText.text = getString(
                R.string.status_failed,
                it.message ?: getString(R.string.calendar_error_toast),
            )
            toast(getString(R.string.calendar_error_toast))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openInStremio(moviePick: MoviePick) {
        val query = buildString {
            append(moviePick.movie.title)
            moviePick.movie.year?.let {
                append(" ")
                append(it)
            }
        }
        val deepLink = Uri.parse("stremio:///search?search=${Uri.encode(query)}")
        val fallback = Uri.parse("https://www.stremio.com/downloads")

        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, deepLink))
        }.recoverCatching {
            if (it is ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, fallback))
            } else {
                throw it
            }
        }.onFailure {
            toast("Unable to open Stremio right now.")
        }
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

    companion object {
        private val REQUIRED_CALENDAR_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )
    }
}
