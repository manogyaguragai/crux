# building crux, start to finish

A running journal of how crux gets built, written for me, the owner, on my first ever app.
Plain language, no assumed Android knowledge. Every milestone adds a section here, and the
file is committed to GitHub each time so the history of the build lives with the code.

No em dashes anywhere, per the project's own writing rule.

---

## 1. the idea

crux is a personal task app for exactly one person: me. It captures a task in one box, files
it under a ranked project, and always keeps the few things that matter today in front of me.

The name comes from climbing: the crux is the hardest move on a route, the point that decides
the whole climb. The app's one job is to keep the crux of my day visible.

Three commitments shape everything:

- **offline first.** every core feature works with no internet. the deterministic core (capture,
  rank, sort, tick) never needs a network.
- **zero budget.** free tiers only. no API key ever ships inside the app.
- **calm, not needy.** no streaks, no badges, no confetti, no nagging. a good climbing partner,
  not a coach.

An optional AI layer comes much later and is always visible, never silent.

## 2. how we got here (before a line of code)

A lot was decided before building, so the build itself has no guesswork. All of it lives in the
`docs/` kit in this repo. The order it was settled in:

1. **naming and concept.** the product brief: what crux is and, just as important, what it is not
   (no iOS, no web, no teams, no month calendar, no gamification).
2. **locked decisions.** the choices that were argued once and are not reopened casually: android
   only, kotlin, dark theme only, four recurrence shapes, four navigation tabs, and so on.
3. **the brand and design system, called "Chisel".**
   - a voice: lowercase, calm, numbers first, no exclamation points.
   - a palette: warm greys plus a family of reds (oxblood, garnet, ember). red is rationed to
     exactly four places in the whole app, so it always means something.
   - three typefaces: Bricolage Grotesque for display, Instrument Sans for interface (its italic
     is the app's soft voice), Geist Mono for anything that is data.
   - signature pieces: "the hold" (an irregular pebble-shaped checkbox), "the bloom" (a slow red
     glow behind the capture bar), "the stone stack" mark used in the logo and the home tab.
   - all of this exists as a brand book and eight screen mockups (the two HTML files in
     `docs/02-design/`).
4. **the flow and architecture.** how data moves (one direction: capture, file, sort, show), the
   exact sort order that ranks tasks, the recurrence math, and the phase plan below.

## 3. the plan: phases are law

The app is built in strict phases. Nothing from a later phase is pulled forward, even when it
looks easy. The point is to prove the spine before adding anything clever.

- **phase 0, foundation.** the empty skeleton that builds and runs. (this is where we are.)
- **phase 1, the spine (no AI).** real capture, projects, the task list, ticking, notifications,
  backup. daily-drivable for two weeks before anything else.
- **phase 2, deterministic intelligence.** typed shortcuts like `#project`, `!p1`, dates, and
  recurrence, all offline, no AI.
- **phase 3, the AI layer.** natural language parsing with a visible, correctable result.
- **phase 4, voice and polish.**

## 4. what has been executed so far

### milestone: environment ready (2026-07-17)

Before any code, the machine had to be able to build android apps. It was missing the two core
pieces, so they were installed into my home folder (no system changes, no admin password):

- **JDK 17** (Java). the build tool runs on Java, so Java has to exist first.
- **the Android SDK** (platform android-36, build-tools, platform-tools). the toolbox that turns
  Kotlin into an installable app and talks to my phone over USB.
- **my phone** was connected and authorized for USB debugging, so builds can install straight onto
  it.

These are now remembered in my shell profile, so every future terminal finds them automatically.

### milestone: phase 0 complete, the foundation builds and runs (2026-07-17)

The empty but real skeleton of the app, built, installed, and confirmed running on my phone:
four themed empty tabs (home, stack, projects, review), the right dark palette and fonts, the
custom stone-stack home icon, and the garnet marker under the active tab. The core-logic unit
tests pass. What exists now:

- a **Gradle project** (`com.crux.app`), the standard android project structure, with all
  dependency versions pinned in one catalog file so they never drift.
- the **database** (Room): the three data shapes (project, task, and a completion log), their
  access objects, and migrations turned on from day one so future data changes are safe.
- the **design tokens as code**: `Color.kt`, `Type.kt`, `Dimens.kt`, `Motion.kt`. every color,
  size, and font in the app comes from these files, never hardcoded. the three Chisel fonts are
  bundled in the app.
- the **theme and the four-tab shell**: home, stack, projects, review. each tab is a themed empty
  screen for now. the tab icons are the real ones drawn from the mockups (the stone stack for
  home), not generic placeholders.
- the **core logic, tested**: the sort order and the recurrence math (the two pieces that carry
  the whole product) are written exactly to spec and covered by unit tests that pass.
- the **parser test table**: the list of example inputs the future parser must handle, parked and
  ignored until phase 2 builds the parser.

### a note on the toolchain (a first-timer lesson)

Getting the versions right took a correction worth remembering. The newest android libraries
(mid 2026) require the very newest build tools and a not-yet-released android version (37). Chasing
"newest of everything" broke the build. The fix was to use the newest *coherent* set that actually
works together: build tools 8.13.2, android 36 (the current ceiling), and libraries from that same
generation. Lesson: in android, versions must match each other, not just be individually newest.
The specific choices are recorded in `DECISIONS.log`.

### milestone: phase 1 begins, capture works (2026-07-17)

The first time the app actually does something. The omnibar on home now takes what you type and
saves it as a task, and the task appears above the bar. It saves to the real database, so it
survives closing the app. No parsing yet (that is phase 2): whatever you type becomes the title,
exactly as planned. Capture is never interrupted, there is no dialog, and the box clears itself so
the next thought goes straight in. Under the hood this added the first repository (the clean
boundary between the screen and the database) and the first view-model (which holds what the screen
shows). Still to come in phase 1: projects, the full task list, checking things off, and reminders.

### milestone: the usable list (2026-07-17)

crux became usable as a task list. The stack tab now shows everything you have added, open and
done. Each task has the hold (the pebble checkbox): tap it and the task fills, its title strikes
through and fades, and it sinks to the bottom, with a five second "undo" if you tapped by mistake.
Yesterday's finished tasks clear the next time you open the app, so each morning starts on a clean
trail. This was the first real test loop: add on home, see it on the stack, check it off. Still to
come in phase 1: ranked projects (so the list groups), editing a task's details, reminders, backup,
and settings.

### milestone: the completion ceremony (2026-07-17)

Ticking a task off got a moment of ceremony. Before, a task struck through and dropped to the
bottom in one instant flick. Now the hold still fills the moment you tap it (that part must feel
instant, so you know the tap registered), but then a line draws across the title, left to right, over
about three-quarters of a second, while the words fade from bright to dim. It is the feeling of
drawing a pen through a finished line on a list. Once that finishes, what happens next depends on
where you are: on the stack the row glides down to the bottom and settles softly; on the home screen,
where only the top three live, it simply fades away to make room for the next thing. Either way it is
a smooth departure, never a hard snap. The five second "undo" appears at that moment, and undoing
pulls the line back quickly. (The timing was tuned by feel over a few tries: two seconds felt like
waiting, one second was still a touch long, three-quarters of a second is the sweet spot between
instant and savoured.)

This is the first place the app deliberately breaks one of its own motion rules. The design system
says no animation should last more than 300 milliseconds; this one lasts a few times that. It earns
the exception on purpose: the whole point is to make finishing a task feel earned and savoured, not
brushed aside. It sits alongside the only other sanctioned slow motions in the app, the two "bloom"
glows. The choice, and why it overrides the rule, is written down in `DECISIONS.log` so the deviation
is never mistaken for a mistake.

One quiet piece of care under the hood: the pause lives with the task's data, not with the screen. So
if you tick a task and immediately swipe to another tab before the line finishes drawing, the task
still completes. The ceremony is visual, but the commitment is real.

## 5. how this journal works

- one section per milestone, newest at the bottom of section 4.
- updated the moment a milestone lands, before anything else.
- committed to GitHub with each update, so the build's story is versioned alongside the code.
- the day to day task tracking lives in `TASKS.md`; this file is the readable narrative.
