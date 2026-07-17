# crux

A personal task app for a single user. It captures a task in one box, files it under a ranked
project, and always keeps the few things that matter today in front of you. The deterministic
core is fully offline. An optional AI layer comes later, and is always visible, never silent.

The name is from climbing: the crux is the hardest move on a route, the point that decides the
whole climb. The app's one job is to keep the crux of your day in view.

> Status: **phase 0 complete.** The foundation builds, installs, and runs: four themed tabs,
> the design system, the database, and the core sort and recurrence logic (tested). No features
> yet. See [`BUILD-PROCESS.md`](BUILD-PROCESS.md) for the full story.

## What it is (and is not)

- Android only, Kotlin, Jetpack Compose, dark theme only.
- Offline first. Every core feature works with no network.
- Single user. No accounts, no server, no sync.
- Not: iOS or web, teams or sharing, a full recurrence engine, a month calendar, or gamification.

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose on a Material 3 substrate (dynamic color off; custom `CruxTheme`) |
| Database | Room, migrations enabled from day one |
| Background | WorkManager + AlarmManager (phase 1) |
| Preferences | DataStore (phase 1) |
| Build | Gradle (Kotlin DSL) with a version catalog |
| Min / target / compile SDK | 26 / 35 / 36 |

Single Gradle module (`:app`). Manual dependency container, no Hilt.

## Project structure

```
com.crux.app
  data/         Room database, DAOs, converters, dependency container
  domain/       entities, the sort comparator, the recurrence engine (pure Kotlin, tested)
  ui/           theme (tokens), components, the four-tab scaffold
  intelligence/ parser golden table (tests only until phase 2)
```

## Build and run

Requires JDK 17 and the Android SDK (platform 36, build-tools, platform-tools). A physical phone
with USB debugging on is the test device.

```bash
./gradlew assembleDebug      # build the app
./gradlew installDebug       # build and install on the connected phone
./gradlew test               # run the JVM unit tests
```

`local.properties` (pointing `sdk.dir` at your SDK) is required and is not committed.

## The plan

Built in strict phases; nothing from a later phase is pulled forward.

0. **foundation** (done): the skeleton that builds and runs.
1. **the spine, no AI**: capture, projects, the list, ticking, notifications, backup.
2. **deterministic intelligence**: typed shortcuts, recurrence, week view. still offline.
3. **the AI layer**: natural language, always visible and correctable.
4. **voice and polish**.

## Design

The look is a system called Chisel: three typefaces (Bricolage Grotesque for display, Instrument
Sans for interface, Geist Mono for data), a warm-grey palette with a rationed family of reds, and
signature pieces like the pebble-shaped checkbox and the slow red glow behind the capture bar.
Every color, size, and duration lives in token files, never hardcoded.

## License

Personal project. The bundled fonts are licensed under the SIL Open Font License.
