# Letterboxd Random Movie Calendar Action

This repository contains:

- a GitHub Action that scrapes a public Letterboxd watchlist, picks a weekly movie, creates a Google Calendar event, and publishes a JSON feed for clients
- a separate Android app in `android-app/` that reads the published weekly movie feed and updates the lock screen wallpaper

The default watchlist is:

- `https://letterboxd.com/coderang/watchlist/`

## What it does

- Scrapes the Letterboxd watchlist HTML directly.
- Follows paginated watchlist pages until no more movie cards are found.
- Picks one deterministic weekly movie from the collected results based on the Sunday event date, so reruns do not reshuffle the week.
- Creates a Google Calendar event for the selected movie.
- Publishes the selected movie to `public/latest-movie.json` for other clients, including the Android wallpaper app.
- Uses a stable event ID so rerunning the workflow does not create duplicates for the same movie on the same date.

## Workflow

The workflow lives at:

- `.github/workflows/random-letterboxd-calendar.yml`

It supports:

- Weekly scheduled runs every Sunday at `22:30` in the `Asia/Kolkata` timezone.
- Manual runs with optional inputs for watchlist URL, event date, timezone, start time, duration, and dry-run mode.

By default, the workflow creates a calendar event for `22:30` in `Asia/Kolkata` with a duration of `150` minutes.

When the workflow runs with `dry_run = false`, it also updates:

- `public/latest-movie.json`

That file is intended to be consumed by the Android app and other lightweight clients.

## Required GitHub Secrets

Add these repository secrets in GitHub:

- `GOOGLE_CALENDAR_ID`
- `GOOGLE_SERVICE_ACCOUNT_EMAIL`
- `GOOGLE_PRIVATE_KEY`

## Google Calendar Setup

1. In Google Cloud, create a project and enable the Google Calendar API.
2. Create a service account and generate a private key for it.
3. Copy the service account email into `GOOGLE_SERVICE_ACCOUNT_EMAIL`.
4. Copy the private key PEM into `GOOGLE_PRIVATE_KEY`.
5. Share the target Google Calendar with the service account email and grant it permission to make changes to events.
6. Put that calendar's ID into `GOOGLE_CALENDAR_ID`.

If you want the action to write into a Workspace user's calendar without manually sharing a calendar to the service account, use domain-wide delegation instead of the simple shared-calendar setup.

## Manual Test

Run the workflow from the GitHub Actions tab with:

- `dry_run = true` to verify scraping and random selection without creating a calendar event.

Or run the script locally:

```bash
DRY_RUN=true node scripts/random-letterboxd-calendar.mjs
```

To verify the published JSON locally:

```bash
cat public/latest-movie.json
```

## Android App

The Android app lives in:

- `android-app/`

It:

- fetches the published movie JSON from the repo
- previews the latest weekly movie
- applies the poster as the lock screen wallpaper
- uses `WorkManager` to check daily for a new weekly movie ID and only updates when the published movie changes

See:

- `android-app/README.md`

## Notes

- The scraper relies on Letterboxd watchlist poster cards exposing `data-item-name` and `data-target-link` in the page HTML.
- The film synopsis/poster enrichment comes from public metadata on each Letterboxd film page.
- The script uses only built-in Node.js modules, so there are no npm dependencies to install.
