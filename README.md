# Milestones

Track personal bests and beat them. Create a goal — books read, longest run,
fastest 5km, best month of revenue — and log a new record every time you top
the last one.

Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

## How it works

Two axes describe every milestone, and between them they cover both halves of
what this app is for — the goals you set for a year, and the records you hold
for life.

|  | **Personal best** — best single result | **Running total** — counts up |
| --- | --- | --- |
| **All time** | Furthest run, fastest 5km | Countries visited |
| **Each year** | Bench 100 kg | Books read, articles published |

- **Measured in** a number (with an optional unit label like `books` or `km`),
  a time (`24:30`, `1:23:45`), or money (`£7,000`).
- **Beating it** means a higher value or a lower one — so distances go up and
  5km times come down, and both count as records. Running totals only climb,
  so they skip this.
- **Target** is optional. Set one and the card grows a progress bar.

Tapping a **personal best** card opens the log sheet, which shows the number
to beat and tells you live how far off you are. Only values that actually beat
the record are saved, so its history is a strictly-improving chain — which is
what makes the progress chart a clean staircase. Tapping a **running total**
card just adds one, immediately, with an undo; long-press and use ＋ to add
with an amount and a note ("Japan"), which is what the History screen then
leads with. Mistyped something? Delete it from the overview and the previous
best takes over.

Reaching a target isn't a dead end: it gets stamped with the date and you're
offered the next one on the same milestone, pre-filled with a suggestion. That
keeps "bench 100kg" and "bench 110kg" as one continuing story instead of two
milestones. Cleared targets stay on the progress chart as faded dashed lines.

A yearly milestone starts fresh every January — its own scoreboard, its own
target — with previous years summarised underneath its chart.

Three screens, swipeable:

| Screen | What it shows |
| --- | --- |
| **History** | Everything logged across every milestone, newest first, grouped by month, with cleared targets called out |
| **Milestones** | The grid, split into "This year" and "All time" — the home screen |
| **Progress** | Per-milestone chart against real dates, with targets as dashed lines |

Everything is local — records live in a DataStore JSON blob on the device.
Export/import from Settings if you want a backup.

## Build

Requires JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

The model layer has no Android dependencies, so its logic — the kind × cadence
matrix, target settling, and reading data written by older versions — is
covered by JVM tests:

```bash
./gradlew :app:testDebugUnitTest
```

For a signed release build, set up `keystore.properties` at the repo root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=main
keyPassword=...
```

then `./gradlew :app:assembleRelease`.

## Release

Cut a new version with the changeset helper:

```bash
./bin/changeset
```

It bumps `versionName` + `versionCode` in `app/build.gradle.kts`, prepends a
new entry to `CHANGELOG.md`, commits, tags `vX.Y.Z`, and pushes — which
triggers `.github/workflows/release.yml` to build, sign, attach the APK to a
GitHub Release, and patch the central manifest. Within ~3 minutes the Groom
Hub app on your phone offers the new version.

## AI Agent

This repo includes an [`agent.md`](agent.md) with a full guide for AI coding agents (and human developers) building on top of the project template — covering architecture, conventions, build config, signing, the release workflow, and more.

## Repo layout

```
.
├── .github/workflows/release.yml   ← release pipeline
├── app/                            ← the Android app module
│   ├── build.gradle.kts
│   ├── src/main/java/dev/matejgroombridge/milestones/
│   │   ├── data/model/             ← Milestone, MilestoneEntry, MilestoneTarget,
│   │   │                             MilestoneKind, MilestoneCadence, MilestoneUnit,
│   │   │                             MilestoneDirection, MilestoneStats
│   │   ├── data/repository/        ← MilestoneRepository (DataStore JSON blob)
│   │   ├── data/settings/          ← Settings + SettingsRepository
│   │   └── ui/                     ← screens, components, theme
│   └── src/test/                   ← model + migration tests (pure JVM)
├── bin/changeset                   ← interactive release helper
├── CHANGELOG.md                    ← human-readable + machine-consumed release notes
├── build.gradle.kts                ← root build file
├── gradle/libs.versions.toml       ← dependency catalog
├── gradle.properties
└── settings.gradle.kts
```
