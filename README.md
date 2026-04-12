# Letterboxd Random Movie Calendar Action

This repository contains a GitHub Action that scrapes a public Letterboxd watchlist, picks a random movie, and creates a Google Calendar event for it.

The default watchlist is:

- `https://letterboxd.com/coderang/watchlist/`

## What it does

- Scrapes the Letterboxd watchlist HTML directly.
- Follows paginated watchlist pages until no more movie cards are found.
- Picks one random movie from the collected results.
- Creates a Google Calendar event for the selected movie.
- Uses a stable event ID so rerunning the workflow does not create duplicates for the same movie on the same date.

## Workflow

The workflow lives at:

- `.github/workflows/random-letterboxd-calendar.yml`

It supports:

- Daily scheduled runs at `09:07` in the `Asia/Kolkata` timezone.
- Manual runs with optional inputs for watchlist URL, event date, timezone, start time, duration, and dry-run mode.

By default, the workflow creates a calendar event for `20:00` in `Asia/Kolkata` with a duration of `150` minutes.

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

## Notes

- The scraper relies on Letterboxd watchlist poster cards exposing `data-item-name` and `data-target-link` in the page HTML.
- The script uses only built-in Node.js modules, so there are no npm dependencies to install.
