# Milestones

Track personal bests and beat them. Create a goal — books read, longest run,
fastest 5km, best month of revenue — and log a new record every time you top
the last one.

Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

## How it works

A **milestone** is a goal plus its history of personal bests:

- **Measured in** a number (with an optional unit label like `books` or `km`),
  a time (`24:30`, `1:23:45`), or money (`£7,000`).
- **Beating it** means a higher value or a lower one — so distances go up and
  5km times come down, and both count as records.
- **Target** is optional. Set one and the card grows a progress bar.

Tapping a card opens the log sheet, which shows the number to beat and tells
you live how far off you are. Only values that actually beat the record are
saved, so a milestone's history is a strictly-improving chain — which is what
makes the progress chart a clean staircase. Mistyped a record? Delete it from
the overview (long-press a card) and the previous best takes over.

Three screens, swipeable:

| Screen | What it shows |
| --- | --- |
| **History** | Every record across every milestone, newest first, grouped by month |
| **Milestones** | The grid of goals with their current bests — the home screen |
| **Progress** | Per-milestone chart of records over time, with the target as a dashed line |

Everything is local — records live in a DataStore JSON blob on the device.
Export/import from Settings if you want a backup.

## Build

Requires JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
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
│   └── src/main/java/dev/matejgroombridge/milestones/
│       ├── data/model/             ← Milestone, MilestoneRecord, MilestoneUnit, MilestoneDirection
│       ├── data/repository/        ← MilestoneRepository (DataStore JSON blob)
│       ├── data/settings/          ← Settings + SettingsRepository
│       └── ui/                     ← screens, components, theme
├── bin/changeset                   ← interactive release helper
├── CHANGELOG.md                    ← human-readable + machine-consumed release notes
├── build.gradle.kts                ← root build file
├── gradle/libs.versions.toml       ← dependency catalog
├── gradle.properties
└── settings.gradle.kts
```
