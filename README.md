# Letterboxd Random Movie Calendar Action

This repository contains:

- a GitHub Action that scrapes a public Letterboxd watchlist, picks a weekly movie, creates a Google Calendar event, and publishes a JSON feed for clients
- a separate Android app in `android-app/` that reads the published weekly movie feed and updates the lock screen wallpaper

The default watchlist is:

- `https://letterboxd.com/coderang/watchlist/`

## Quick Start

If you want to use the full system yourself:

1. Fork this repository.
2. Set up a Google Cloud project, service account, and a Google Calendar the bot can edit.
3. Add the required GitHub repository secrets.
4. Push to `main` and run the workflow once manually.
5. Confirm that `public/latest-movie.json` was updated and that a calendar event was created.
6. Install the Android app from `android-app/` and point it to your published JSON feed if needed.

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
- `TMDB_API_KEY`

## End-to-End Setup

### 1. Fork and enable GitHub Actions

1. Fork this repository to your own GitHub account.
2. Open your fork on GitHub.
3. Make sure GitHub Actions are enabled for the repository.
4. Use the `main` branch as the branch that publishes `public/latest-movie.json`.

## Google Calendar Setup

1. In Google Cloud, create a project and enable the Google Calendar API.
2. Create a service account and generate a private key for it.
3. Copy the service account email into `GOOGLE_SERVICE_ACCOUNT_EMAIL`.
4. Copy the private key PEM into `GOOGLE_PRIVATE_KEY`.
5. Share the target Google Calendar with the service account email and grant it permission to make changes to events.
6. Put that calendar's ID into `GOOGLE_CALENDAR_ID`.

If you want the action to write into a Workspace user's calendar without manually sharing a calendar to the service account, use domain-wide delegation instead of the simple shared-calendar setup.

### 2. Get your Google Calendar ID

1. Open Google Calendar in the browser.
2. In the left sidebar, find the calendar you want the action to write into.
3. Open that calendar's settings.
4. Under `Integrate calendar`, copy the `Calendar ID`.
5. For a primary personal calendar, the ID is often your Gmail address.

### 3. Share the calendar with the service account

1. Copy your service account email.
2. In Google Calendar, open `Settings and sharing` for the target calendar.
3. Under `Share with specific people or groups`, add the service account email.
4. Grant it `Make changes to events`.

Without writer access, Google Calendar will reject event creation with a `403 requiredAccessLevel` error.

### 4. Add GitHub repository secrets

In your fork, go to `Settings` -> `Secrets and variables` -> `Actions` and add:

- `GOOGLE_CALENDAR_ID`: your calendar ID
- `GOOGLE_SERVICE_ACCOUNT_EMAIL`: the service account email
- `GOOGLE_PRIVATE_KEY`: the full private key value from the service account JSON
- `TMDB_API_KEY`: your TMDb API key for true poster images

For `GOOGLE_PRIVATE_KEY`, paste the full key including the `BEGIN PRIVATE KEY` and `END PRIVATE KEY` lines.

### 5. Optional repository customization

You can edit:

- [scripts/random-letterboxd-calendar.mjs](/Users/gkhatavk/Documents/GitHub/letterboxd-random-picker/scripts/random-letterboxd-calendar.mjs) to change defaults
- [.github/workflows/random-letterboxd-calendar.yml](/Users/gkhatavk/Documents/GitHub/letterboxd-random-picker/.github/workflows/random-letterboxd-calendar.yml) to change the schedule

Common customizations:

- change the default Letterboxd watchlist URL
- change the scheduled day and time
- change the timezone
- change the event duration

### 6. Run the workflow manually the first time

1. Open the `Actions` tab in GitHub.
2. Open the `Random Letterboxd Calendar` workflow.
3. Click `Run workflow`.
4. Start with `dry_run = true` if you only want to test scraping and selection.
5. Run again with `dry_run = false` when you want to create the calendar event and publish the JSON feed.

### 7. Verify it worked

Check these places:

1. The workflow run logs in GitHub Actions.
2. Your Google Calendar for a new `Watch: ...` event.
3. [public/latest-movie.json](/Users/gkhatavk/Documents/GitHub/letterboxd-random-picker/public/latest-movie.json) in your repository.

You should see:

- the selected movie title and year
- the generated event date and time
- the poster URL
- the synopsis and metadata

### 8. Use the published feed with the Android app

The app uses the raw GitHub URL for `public/latest-movie.json`.

Default format:

```text
https://raw.githubusercontent.com/<your-github-username>/<your-repo-name>/main/public/latest-movie.json
```

If you changed the repository name or owner, update the feed URL inside the Android app.

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

For Android setup, install, signing, and release steps, see:

- [android-app/README.md](/Users/gkhatavk/Documents/GitHub/letterboxd-random-picker/android-app/README.md)

## Notes

- The scraper relies on Letterboxd watchlist poster cards exposing `data-item-name` and `data-target-link` in the page HTML.
- The film synopsis/poster enrichment comes from public metadata on each Letterboxd film page.
- If `TMDB_API_KEY` is configured, the workflow prefers TMDb poster art so the Android wallpaper app gets a true movie poster instead of a share-style image.
- The script uses only built-in Node.js modules, so there are no npm dependencies to install.
