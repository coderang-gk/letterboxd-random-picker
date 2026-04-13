import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { createHash, createSign } from "node:crypto";

const DEFAULT_WATCHLIST_URL = "https://letterboxd.com/coderang/watchlist/";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_CALENDAR_API_BASE = "https://www.googleapis.com/calendar/v3";
const GOOGLE_CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";
const PAGE_LIMIT = Number.parseInt(process.env.PAGE_LIMIT ?? "25", 10);

async function main() {
  const config = readConfig();

  const movies = await scrapeAllMovies(config.watchlistUrl, config.pageLimit);
  if (movies.length === 0) {
    throw new Error(`No movies found at ${config.watchlistUrl}`);
  }

  const eventWindow = buildEventWindow(config);
  const pickedMovie = pickMovieForDate(
    movies,
    eventWindow.targetDate,
    config.watchlistUrl,
  );
  const movieDetails = await fetchMovieDetails(pickedMovie.link);
  const event = buildCalendarEvent(
    pickedMovie,
    movieDetails,
    config,
    eventWindow,
  );
  const publishedMovie = buildPublishedMovie(
    movies.length,
    pickedMovie,
    movieDetails,
    config,
    eventWindow,
    event,
  );

  await writePublishedMovie(config.outputJsonPath, publishedMovie);

  console.log(
    JSON.stringify(
      {
        publishedMovie,
        dryRun: config.dryRun,
      },
      null,
      2,
    ),
  );

  if (config.dryRun) {
    console.log("Dry run enabled. Skipping Google Calendar API calls.");
    return;
  }

  const accessToken = await getAccessToken(config);
  const alreadyExists = await findExistingEvent(
    config.calendarId,
    event.id,
    accessToken,
  );

  if (alreadyExists) {
    console.log(
      `Event ${event.id} already exists for ${pickedMovie.displayName}. No new event created.`,
    );
    console.log(JSON.stringify(alreadyExists, null, 2));
    return;
  }

  const createdEvent = await insertEvent(config.calendarId, event, accessToken);
  console.log("Created Google Calendar event:");
  console.log(JSON.stringify(createdEvent, null, 2));
}

function readConfig() {
  const watchlistUrl = normalizeWatchlistUrl(
    process.env.WATCHLIST_URL?.trim() || DEFAULT_WATCHLIST_URL,
  );
  const eventTimezone = process.env.EVENT_TIMEZONE?.trim() || "Asia/Kolkata";
  const startHour = parseNumberEnv("EVENT_START_HOUR", 22, 0, 23);
  const startMinute = parseNumberEnv("EVENT_START_MINUTE", 30, 0, 59);
  const durationMinutes = parseNumberEnv("EVENT_DURATION_MINUTES", 150, 1, 1440);
  const pageLimit = Number.isFinite(PAGE_LIMIT) && PAGE_LIMIT > 0 ? PAGE_LIMIT : 25;
  const dryRun = parseBoolean(process.env.DRY_RUN);

  const config = {
    watchlistUrl,
    eventTimezone,
    startHour,
    startMinute,
    durationMinutes,
    eventDate: normalizeEventDate(process.env.EVENT_DATE?.trim() || ""),
    pageLimit,
    dryRun,
    outputJsonPath: process.env.OUTPUT_JSON_PATH?.trim() || "public/latest-movie.json",
    calendarId: process.env.GOOGLE_CALENDAR_ID?.trim() || "",
    serviceAccountEmail:
      process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL?.trim() || "",
    privateKey: normalizePrivateKey(process.env.GOOGLE_PRIVATE_KEY ?? ""),
  };

  if (!dryRun) {
    for (const [key, value] of Object.entries({
      GOOGLE_CALENDAR_ID: config.calendarId,
      GOOGLE_SERVICE_ACCOUNT_EMAIL: config.serviceAccountEmail,
      GOOGLE_PRIVATE_KEY: config.privateKey,
    })) {
      if (!value) {
        throw new Error(`Missing required environment variable: ${key}`);
      }
    }
  }

  return config;
}

function pickMovieForDate(movies, targetDate, watchlistUrl) {
  const digest = createHash("sha256")
    .update(`${targetDate}:${watchlistUrl}`)
    .digest("hex");
  const numericSeed = Number.parseInt(digest.slice(0, 12), 16);
  const index = numericSeed % movies.length;

  return movies[index];
}

function normalizePrivateKey(value) {
  let normalized = value.trim();

  if (
    (normalized.startsWith('"') && normalized.endsWith('"')) ||
    (normalized.startsWith("'") && normalized.endsWith("'"))
  ) {
    normalized = normalized.slice(1, -1);
  }

  normalized = normalized.replace(/\\n/g, "\n").replace(/\r\n/g, "\n");

  if (
    normalized &&
    !normalized.includes("-----BEGIN PRIVATE KEY-----")
  ) {
    throw new Error(
      "GOOGLE_PRIVATE_KEY does not look like a PEM private key. Paste the full private_key value from the service account JSON, including BEGIN/END lines.",
    );
  }

  return normalized;
}

function normalizeEventDate(value) {
  if (!value) {
    return "";
  }

  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error(
      `EVENT_DATE must use YYYY-MM-DD format. Received: ${value}`,
    );
  }

  return value;
}

function parseNumberEnv(name, fallback, min, max) {
  const raw = process.env[name];
  if (!raw) {
    return fallback;
  }

  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) {
    throw new Error(
      `${name} must be an integer between ${min} and ${max}. Received: ${raw}`,
    );
  }

  return parsed;
}

function parseBoolean(value) {
  return /^(1|true|yes)$/i.test(value ?? "");
}

function normalizeWatchlistUrl(value) {
  const url = new URL(value);
  if (!url.pathname.endsWith("/")) {
    url.pathname = `${url.pathname}/`;
  }
  return url.toString();
}

async function scrapeAllMovies(watchlistUrl, pageLimit) {
  const seenLinks = new Set();
  const movies = [];

  for (let page = 1; page <= pageLimit; page += 1) {
    const pageUrl = buildWatchlistPageUrl(watchlistUrl, page);
    const html = await fetchText(pageUrl, { allowNotFound: page > 1 });
    if (html === null) {
      break;
    }
    const pageMovies = extractMovies(html);

    if (pageMovies.length === 0) {
      if (page === 1) {
        throw new Error(`Watchlist page returned no movie cards: ${pageUrl}`);
      }
      break;
    }

    let addedFromPage = 0;
    for (const movie of pageMovies) {
      if (seenLinks.has(movie.link)) {
        continue;
      }

      seenLinks.add(movie.link);
      movies.push(movie);
      addedFromPage += 1;
    }

    if (addedFromPage === 0) {
      break;
    }
  }

  return movies;
}

function buildWatchlistPageUrl(watchlistUrl, page) {
  const url = new URL(watchlistUrl);
  if (page === 1) {
    return url.toString();
  }

  url.pathname = `${url.pathname}page/${page}/`;
  return url.toString();
}

function extractMovies(html) {
  const componentTags = html.match(
    /<div class="react-component"[^>]*data-component-class="LazyPoster"[^>]*>/g,
  );

  if (!componentTags) {
    return [];
  }

  return componentTags
    .map((tag) => {
      const displayName = decodeHtmlEntities(getAttribute(tag, "data-item-name"));
      const relativeLink = decodeHtmlEntities(
        getAttribute(tag, "data-target-link"),
      );

      if (!displayName || !relativeLink) {
        return null;
      }

      const yearMatch = displayName.match(/\((\d{4})\)\s*$/);
      const title = yearMatch
        ? displayName.slice(0, yearMatch.index).trim()
        : displayName.trim();
      const year = yearMatch?.[1] ?? null;

      return {
        title,
        year,
        displayName,
        link: new URL(relativeLink, "https://letterboxd.com").toString(),
      };
    })
    .filter(Boolean);
}

function getAttribute(tag, attributeName) {
  const escapedName = attributeName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = tag.match(new RegExp(`${escapedName}="([^"]*)"`, "i"));
  return match?.[1] ?? "";
}

function decodeHtmlEntities(value) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function buildEventWindow(config) {
  const targetDate = config.eventDate || resolveDefaultEventDate(config);
  const endOffset = config.startHour * 60 + config.startMinute + config.durationMinutes;
  const spansNextDay = endOffset >= 24 * 60;
  const endDate = spansNextDay ? addDays(targetDate, 1) : targetDate;
  const endHour = Math.floor((endOffset % (24 * 60)) / 60);
  const endMinute = endOffset % 60;

  return {
    targetDate,
    endDate,
    startDateTime: `${targetDate}T${pad(config.startHour)}:${pad(config.startMinute)}:00`,
    endDateTime: `${endDate}T${pad(endHour)}:${pad(endMinute)}:00`,
  };
}

function buildPublishedMovie(
  scrapedMovieCount,
  pickedMovie,
  movieDetails,
  config,
  eventWindow,
  event,
) {
  return {
    id: event.id,
    generatedAt: new Date().toISOString(),
    watchlistUrl: config.watchlistUrl,
    scrapedMovieCount,
    movie: {
      ...pickedMovie,
      synopsis: movieDetails.synopsis,
      posterUrl: movieDetails.posterUrl,
      genres: movieDetails.genres,
      directors: movieDetails.directors,
    },
    event: {
      summary: event.summary,
      description: event.description,
      date: eventWindow.targetDate,
      start: event.start,
      end: event.end,
    },
  };
}

async function writePublishedMovie(outputJsonPath, publishedMovie) {
  const directory = dirname(outputJsonPath);
  await mkdir(directory, { recursive: true });
  await writeFile(
    outputJsonPath,
    `${JSON.stringify(publishedMovie, null, 2)}\n`,
    "utf8",
  );
}

async function fetchMovieDetails(movieUrl) {
  const html = await fetchText(movieUrl);
  const synopsis =
    decodeHtmlEntities(extractMetaContent(html, "property", "og:description")) ||
    decodeHtmlEntities(extractMetaContent(html, "name", "description"));
  const posterUrl = decodeHtmlEntities(
    extractMetaContent(html, "property", "og:image"),
  );
  const jsonLd = extractMovieJsonLd(html);

  return {
    synopsis: synopsis || "",
    posterUrl: posterUrl || "",
    genres: normalizeStringList(jsonLd?.genre),
    directors: normalizePeopleList(jsonLd?.director),
  };
}

function resolveDefaultEventDate(config) {
  const now = getZonedDateParts(new Date(), config.eventTimezone);
  const today = `${now.year}-${pad(now.month)}-${pad(now.day)}`;
  const isSunday = getWeekday(today) === 0;
  const isScheduledRun = process.env.GITHUB_EVENT_NAME === "schedule";

  // Scheduled Sunday runs should create a Sunday event even if the workflow
  // starts a bit late.
  if (isScheduledRun && isSunday) {
    return today;
  }

  return nextWeekdayOnOrAfter(today, 0, {
    includeToday: isSunday,
  });
}

function getZonedDateParts(date, timeZone) {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });

  const parts = Object.fromEntries(
    formatter
      .formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value]),
  );

  return {
    year: Number.parseInt(parts.year, 10),
    month: Number.parseInt(parts.month, 10),
    day: Number.parseInt(parts.day, 10),
    hour: Number.parseInt(parts.hour, 10),
    minute: Number.parseInt(parts.minute, 10),
    second: Number.parseInt(parts.second, 10),
  };
}

function addDays(dateString, amount) {
  const date = new Date(`${dateString}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

function getWeekday(dateString) {
  const date = new Date(`${dateString}T00:00:00Z`);
  return date.getUTCDay();
}

function nextWeekdayOnOrAfter(dateString, weekday, options = {}) {
  const currentWeekday = getWeekday(dateString);
  let daysUntil = (weekday - currentWeekday + 7) % 7;

  if (daysUntil === 0 && options.includeToday === false) {
    daysUntil = 7;
  }

  return addDays(dateString, daysUntil);
}

function buildCalendarEvent(movie, movieDetails, config, eventWindow) {
  const eventId = buildStableEventId(movie, eventWindow.targetDate);
  const descriptionLines = [
    "Random movie picked from a Letterboxd watchlist.",
    `Movie: ${movie.displayName}`,
  ];

  if (movieDetails.directors.length > 0) {
    descriptionLines.push(`Director: ${movieDetails.directors.join(", ")}`);
  }

  if (movieDetails.genres.length > 0) {
    descriptionLines.push(`Genres: ${movieDetails.genres.join(", ")}`);
  }

  if (movieDetails.synopsis) {
    descriptionLines.push(`Synopsis: ${movieDetails.synopsis}`);
  }

  if (movieDetails.posterUrl) {
    descriptionLines.push(`Poster: ${movieDetails.posterUrl}`);
  }

  descriptionLines.push(`Letterboxd: ${movie.link}`);
  descriptionLines.push(`Watchlist: ${config.watchlistUrl}`);

  return {
    id: eventId,
    summary: `Watch: ${movie.displayName}`,
    description: descriptionLines.join("\n"),
    source: {
      title: "Letterboxd Watchlist",
      url: movie.link,
    },
    start: {
      dateTime: eventWindow.startDateTime,
      timeZone: config.eventTimezone,
    },
    end: {
      dateTime: eventWindow.endDateTime,
      timeZone: config.eventTimezone,
    },
  };
}

function buildStableEventId(movie, targetDate) {
  return `lb${createHash("sha256")
    .update(`${targetDate}:${movie.link}`)
    .digest("hex")
    .slice(0, 30)}`;
}

async function getAccessToken(config) {
  const nowInSeconds = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(
    JSON.stringify({ alg: "RS256", typ: "JWT" }),
  );
  const payload = base64UrlEncode(
    JSON.stringify({
      iss: config.serviceAccountEmail,
      scope: GOOGLE_CALENDAR_SCOPE,
      aud: GOOGLE_TOKEN_URL,
      iat: nowInSeconds,
      exp: nowInSeconds + 3600,
    }),
  );
  const unsignedToken = `${header}.${payload}`;

  const signer = createSign("RSA-SHA256");
  signer.update(unsignedToken);
  signer.end();

  const signature = signer.sign(config.privateKey);
  const assertion = `${unsignedToken}.${toBase64Url(signature)}`;

  const response = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!response.ok) {
    throw new Error(
      `Failed to fetch Google access token (${response.status}): ${await response.text()}`,
    );
  }

  const json = await response.json();
  if (!json.access_token) {
    throw new Error("Google token response did not include an access_token.");
  }

  return json.access_token;
}

async function findExistingEvent(calendarId, eventId, accessToken) {
  const response = await fetch(
    `${GOOGLE_CALENDAR_API_BASE}/calendars/${encodeURIComponent(
      calendarId,
    )}/events/${encodeURIComponent(eventId)}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(
      `Failed to check for existing event (${response.status}): ${await response.text()}`,
    );
  }

  return response.json();
}

async function insertEvent(calendarId, event, accessToken) {
  const response = await fetch(
    `${GOOGLE_CALENDAR_API_BASE}/calendars/${encodeURIComponent(
      calendarId,
    )}/events`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(event),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Failed to create calendar event (${response.status}): ${await response.text()}`,
    );
  }

  return response.json();
}

async function fetchText(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "User-Agent":
        "letterboxd-random-picker/1.0 (+https://github.com/actions/runner)",
      Accept: "text/html,application/xhtml+xml",
    },
  });

  if (response.status === 404 && options.allowNotFound) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Request failed for ${url} (${response.status})`);
  }

  return response.text();
}

function base64UrlEncode(value) {
  return toBase64Url(Buffer.from(value, "utf8"));
}

function toBase64Url(buffer) {
  return buffer
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function pad(value) {
  return String(value).padStart(2, "0");
}

function extractMetaContent(html, attributeName, attributeValue) {
  const escapedValue = attributeValue.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = html.match(
    new RegExp(
      `<meta[^>]*${attributeName}="${escapedValue}"[^>]*content="([^"]*)"[^>]*>`,
      "i",
    ),
  );

  return match?.[1] ?? "";
}

function extractMovieJsonLd(html) {
  const scripts = [...html.matchAll(
    /<script type="application\/ld\+json">([\s\S]*?)<\/script>/gi,
  )];

  for (const [, rawJson] of scripts) {
    try {
      const parsed = JSON.parse(rawJson);
      if (parsed?.["@type"] === "Movie") {
        return parsed;
      }
    } catch {
      // Ignore non-JSON or unrelated JSON-LD blocks.
    }
  }

  return null;
}

function normalizeStringList(value) {
  if (Array.isArray(value)) {
    return value.filter((item) => typeof item === "string" && item.trim());
  }

  if (typeof value === "string" && value.trim()) {
    return [value];
  }

  return [];
}

function normalizePeopleList(value) {
  const values = Array.isArray(value) ? value : [value];

  return values
    .map((person) => {
      if (typeof person === "string") {
        return person;
      }

      return person?.name ?? "";
    })
    .filter(Boolean);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
