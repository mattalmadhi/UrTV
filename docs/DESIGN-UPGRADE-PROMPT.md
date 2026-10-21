# Prompt: a design upgrade for «شاشة تلفزيونك» (club badges + backgrounds from every region)

You are working in the UrTV repo (https://github.com/tshi-dev/UrTV). Read `README.md`, `src/template.html`,
`src/build.py`, `photos/index.json`, `docs/PHOTO-CREDITS.md`, `android/launcher/src/main/java/sa/urtv/home/MainActivity.java`
(photo part) and `tests/page.test.mjs` before you change anything. The screensaver page is `src/template.html`;
`src/build.py` turns it into `dist/tv.html` and `dist/urtv.html`. The home screen (launcher) uses the same
`photos/index.json`.

## What the screen is today (as a UX reviewer read it)

Layout on a 1920×1080 TV: Hijri/Gregorian date top-right; weather + markets (تاسي، S&P 500، بيتكوين) top-left;
a very large Thmanyah clock centre-right with «بتوقيت <city>»; a card left of the clock (verse/hadith, shows,
prayers…); a bottom row of three panels: next prayer, Ramadan countdown, matches. The palette is sand and gold on a
dark green-black. A photo fills the screen behind a dim layer, a grain layer and top/bottom veils, rotating every
30 minutes from a set of 5 that changes every 4 days.

What already works and must stay: the type scale and the clock as the hero; the gold/sand palette; RTL and Arabic
digits; the pixel shift against burn-in; the "settle then hold still" photo motion (no endless animation: the TV
has ~2 GB RAM and a 32-bit ARM CPU); no `backdrop-filter`; the offline fallback to bundled photos.

Problems to fix:

1. **The backgrounds are not "all of Saudi Arabia".** 75 of the 86 hosted photos are NEOM (Tabuk region); Riyadh
   has one; most regions have none. Photos have no region, place, or time-of-day data, so the rotation is random
   and can show a bright midday desert at 2 a.m.
2. **The photo often reads as muddy.** Every photo gets the same veils and grain plus a per-photo dim; on a
   hazy photo (e.g. dusty Riyadh) the whole screen turns flat grey-brown and the photo stops being a "place".
3. **Photo changes are a hard cut** in the screensaver (the launcher already crossfades with two layers).
4. **Matches are plain text** («القادسية × الهلال»): no badges, no visual difference between an upcoming match,
   a live one and a result, and the followed team is not highlighted.
5. **The Ramadan countdown takes a third of the bottom row all year** (e.g. «١١٠ يوم»), which is low value most
   of the year.
6. **The place is never named.** Credits are off-screen (fine), but the viewer never learns where the photo is,
   which is the cheapest way to make the screen feel curated.

## What to build

### A. Backgrounds that cover all 13 regions, in a pattern

1. Extend each entry in `photos/index.json` (keep `f`, `c`, `d`) with:
   - `r`: region id, one of `riyadh, makkah, madinah, qassim, eastern, asir, tabuk, hail, northern, jazan, najran, baha, jouf`
   - `p`: place name in Arabic (short: «العُلا», «جبال السودة», «كورنيش الخبر»)
   - `t`: time of day: `day`, `golden` (sunrise/sunset) or `night`
   - `l`: measured mean luminance 0–1 of the top 35% and bottom 30% of the photo (where the text sits)
   Tag the existing 86 photos (most are `tabuk`).
2. Add photos so that **every region has at least 6, at least 1 of them `night` and 1 `golden`**. Use only
   Unsplash (Unsplash License) or other sources whose license allows this use; add every new photo to
   `docs/PHOTO-CREDITS.md` with photographer and source link, same format as today. Same size and JPEG quality as
   the current files. Do not use photos with readable faces in close-up, logos, or text.
3. Rotation pattern (deterministic, same result in the screensaver and the launcher, Riyadh time):
   - **Each day is one region**, in a fixed geographic loop (north → west → south → east → centre:
     tabuk, jouf, northern, hail, qassim, madinah, makkah, baha, asir, jazan, najran, eastern, riyadh), so all 13
     come round in 13 days.
   - **Friday is always the household's own region** (from the chosen city; `Cities.java` already groups cities
     by region).
   - Within the day, rotate every 30 minutes (as now) **only through photos whose `t` matches the hour**:
     `golden` within ±75 min of sunrise/sunset (the page already computes prayer times, so use Fajr/Maghrib),
     `night` after Isha until Fajr, `day` otherwise. If a region has no photo for that time, take one from the
     same region with any `t`, then fall back to the old behaviour.
   - Never show the same photo twice in a row.
   - Keep the launcher (`MainActivity.java`) on the same pattern so the home screen and the screensaver agree.
4. **Crossfade** in the screensaver: two stacked `.photo` layers, fade the new one in over ~1.5 s using only
   `opacity`, then remove the old one. Keep the `settle` motion on the new layer only.
5. **Per-photo scrim instead of a fixed one**: use `l` to set the strength of the top and bottom veils
   (bright photo → stronger veil, dark photo → lighter veil) so text contrast stays ≥ 4.5:1 without crushing a
   dark photo. Lower the grain to near-invisible on `night` photos.
6. **Name the place**, quietly: a small line at bottom-left, above the matches panel, in the dim colour
   (`--dim`), same size as `.sub`: «العُلا · منطقة المدينة المنورة». It fades in with the photo and stays
   inside the pixel shift.

### B. Club badges, done with taste

1. Badge source: TheSportsDB, already used for the fixtures. Use the event's `strHomeTeamBadge` /
   `strAwayTeamBadge` when present, otherwise `lookupteam.php?id=<id>` → `strBadge`. Request the small rendition
   (append `/small` to the badge URL; fall back to the full URL if that fails). **Cache** each badge (app disk via
   `PhotoCache.java`, `localStorage` data-URL in the page) and refresh at most weekly. Do not commit badge files to
   the repo: they are the clubs' trademarks, fetched at run time like the fixtures.
2. A match block becomes: **badge · name** on each side, the centre showing the time («٩:٠٠ م»), the live score,
   or the final score. Badges sit in a fixed square (about 1.5× the team-name line height, `object-fit: contain`)
   so different shapes line up, with a soft dark halo (`drop-shadow`, no box) so dark badges stay readable on the
   dark photo. Never stretch, recolour or crop a badge.
3. If a badge is missing or fails to load: a circular monogram (first Arabic letter of the team, Thmanyah Display,
   gold ring), never a broken-image icon.
4. States:
   - **upcoming**: as now, «بعد يومين و١٦ ساعة · ٩:٠٠ م» under the block;
   - **live**: a small gold dot that fades in and out every 2 s (opacity only, only while live), the minute
     («الدقيقة ٦٣»), the score in `#FFFCF2`;
   - **final** (for the hours the page already keeps results): the winning side at full colour, the losing side at
     70% opacity, «النتيجة النهائية».
   - The **followed team** gets a 2px underline in the club's colour (`strColour1` from `lookupteam.php`;
     if it is too dark for the background, use the gold instead).
5. **Match day takes the stage**: from 3 hours before kickoff of a followed team until the final whistle, the card
   beside the clock switches by itself to a big version of that match (badges ~3× larger, names, time or live
   score), then returns to the household's chosen card. Two followed teams playing at once: show both, stacked.

### C. The bottom row, made contextual

Replace the fixed Ramadan panel with **«المناسبة القادمة»**: the nearest of Ramadan, Eid al-Fitr, Eid al-Adha,
the Day of Arafah, National Day (23 September) and Founding Day (22 February), with «بعد ٢٣ يوم» and the date
(Hijri occasions from the Umm al-Qura calendar the page already uses). During Ramadan it shows the day of Ramadan
and the time to Iftar. Keep the panel width and type.

## Rules

- Keep everything that is listed as "must stay" above. No new fonts, no frameworks, no `backdrop-filter`, no
  animation that runs forever (the live dot only while a match is live).
- Arabic text, Arabic digits and RTL everywhere, matching what is there.
- Everything works offline: no badge → monogram; no network → bundled photos (add one bundled photo per region to
  `assets/backgrounds/` and `backgrounds.json`, so the region pattern still works offline, and keep the APK
  growth under ~4 MB).
- Rebuild with `python3 src/build.py`, run the tests in `tests/` (add tests for the region/day/time pattern and
  for the badge fallback in `tests/page.test.mjs`), and open `dist/urtv.html` in a browser at 1920×1080 to look at
  it. Then check the matches panel with 1 and 2 followed teams, upcoming / live / final, and a missing badge.
- Screenshots to attach to the PR: day, golden hour and night photos; a match day; a missing badge; the launcher.
- Update `docs/PHOTO-CREDITS.md` and the README's description of the screensaver.
