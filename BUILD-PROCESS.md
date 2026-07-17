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

### milestone: capture stops hiding, and projects arrive (2026-07-17)

Two things landed together. First, a small but real annoyance: when you tapped the capture bar, the
phone's keyboard slid up and covered half of it, so you could not see what you were typing. The app
draws edge to edge (all the way under the system bars), which looks clean but means the app has to
lift its own content out of the keyboard's way. It now does: the whole bottom shell, the tab bar and
the capture bar riding above it, rises to sit just above the keyboard. Capture is fully visible while
you type, which is the whole point of a capture-first app.

Second, the projects tab became real. Until now every task lived in one flat pile. Projects are the
app's way of ranking what matters: you name a project, and its position in the list is its weight,
with rank one at the top carrying the most. The tab lets you name a project, and enter an "edit" mode
to rename one, nudge it up or down the order, or archive it. A few deliberate choices: re-ranking is
plain up and down arrows rather than drag-to-reorder (drag is a notorious time sink to build well,
and the product only needs the order to be editable, not draggable); each row shows its position as a
simple number, so archiving one never leaves a gap in the count; and names have to be unique, so
trying to reuse one shows a calm "a project with that name exists" without ever throwing away what
you typed. Archiving is a soft delete for now, with the confirmation step and a permanent "clear
archived" purge deferred to the settings screen, because nothing is filed under a project yet.

The tab is the first half of a larger idea. The next steps make it pay off: letting a task belong to
a project (the task detail screen), and then grouping the stack by project rank so the list finally
organises itself, with the catch-all inbox always last.

### milestone: a task gets a detail screen (2026-07-17)

Until now a task was just a line of text. Now tapping a task opens a screen where you can shape it.
You can rename it, file it under a project, set its priority from one to four (priority one wears the
one bit of red the screen allows, because it is the thing that matters most), give it a due date and
a time of day, and make it repeat, daily, on weekdays, weekly, or monthly. There is a notes field for
anything that does not fit in a title, and a quiet line at the very bottom that records how the task
arrived, for example "added jul 17 · typed". Later, when the app can capture by voice or read your
shorthand, that line will say so; it is the task remembering its own history.

Everything you change saves the instant you change it, and it survives leaving the screen or even
turning the phone sideways, because the truth lives in the database, not on the screen. A couple of
honest notes: the date and time use the phone's own standard pickers (they happen to pick up the
app's red, so they do not look out of place), and for a weekly or monthly repeat the app quietly
assumes the day from the due date rather than asking again, which keeps the screen simple; a proper
day picker is on the list for later. This is the piece that unlocks the next one: now that a task can
belong to a project, the stack can finally group itself by project.

### milestone: the stack organises itself (2026-07-17)

This is the payoff of the last two milestones, and the moment the app's core idea is finally whole.
The stack used to be one long flat list. Now it groups itself by project, in the order you ranked
those projects, with a catch-all "inbox" always sitting at the bottom for anything you have not filed
yet. Each group wears its name and a short red underline that fades to nothing, the app's one small
flourish for a section heading. A project with nothing in it shows no heading at all, so the screen
stays as quiet as the work allows. And if you ever archive a project, its tasks are not lost, they
simply fall back into the inbox.

The grouping logic is deliberately kept as a small, pure piece of arithmetic, separated from the
screen, so it could be covered by fast tests that do not need a phone: projects come out in rank
order, the inbox lands last, empty groups disappear, orphaned tasks go to the inbox, and finished
tasks still settle to the bottom of their own group. All of it is wired so that the instant you give
a task a project on its detail screen, it hops into the right group on the stack.

With this, the spine the whole plan set out to prove is complete: you can capture a thought, file it
under a ranked project, let the app sort it, and tick it off. Everything from here, reminders,
backup, settings, and later the intelligence, hangs off this working skeleton.

### milestone: settings, and a clean slate (2026-07-17)

A gear now sits quietly in the top corner of home, and behind it is the first settings screen. It is
deliberately small. There are two ways to change how the app looks: a "deep" switch that turns the
already-dark background to true black, which an OLED screen renders as switched-off pixels, and a
text-size control with four steps for anyone who wants the words larger or smaller. Both changes take
effect everywhere in the app at once and are remembered between launches.

The important button lives under "data": reset everything. It erases every task, every project, and
every setting, and hands you back a brand-new app. Because that cannot be undone, it does not happen
on a single tap; it first replaces itself with a plain-spoken warning and a red "erase everything" to
confirm, styled in the app's own look rather than a generic system pop-up. This is the button the
owner asked for so they could clear out all the throwaway data from building and start tracking real
work. Running it did exactly that: the test tasks and projects from every milestone above are gone,
and the app is as fresh as the day it first ran.

Under the hood, two small pieces of craft: the text-size setting works by quietly telling the whole
app to treat every size as a little bigger or smaller, so not one line of the screens had to change;
and the true-black setting flows from a single switch point, so the background can change in one place
and every screen follows. What is not here yet, on purpose, are the notification switches and the
backup buttons; they will arrive alongside the features they control, rather than sitting dead now.

### milestone: the rows learn to speak (2026-07-17)

Now that a task can carry a priority and a date, the list started showing them. Under each task title
there is a quiet second line: how urgent it is, and when it is due. A date that has already passed
shows in red, one coming up in the next couple of days in gold, and anything further off stays muted;
a task with neither a priority nor a date shows nothing extra, so the list stays calm. Home also grew
a small count in its top corner: "2 overdue", in the app's warm red, but only when something actually
is. There was one careful detail here worth noting: a task due "today" with no particular time is not
treated as late the instant midnight passes; it only becomes overdue once the whole day is behind you.
That single rule lives in one place and is covered by tests, so the row and the count can never
disagree about what "late" means.

### milestone: the app can reach out (2026-07-17)

Until now crux only spoke when you opened it. Now it can send two gentle notifications a day: a
morning one, "the day's climb", and an evening "wrap". Each is a calm one-line count, never a list of
demands, for example "3 open, 1 overdue" in the morning or "0 done today, 3 still open" at night. Both
can be switched off, and, at the owner's request, the times are yours to set: tap the time next to
either one and pick when it should arrive. There is also a "due" switch for per-task reminders, which
is wired but not yet firing; that piece needs a bit more plumbing and comes next.

The scheduling is done in a way that quietly survives a phone restart without any extra machinery, and
each notification politely reschedules the next day's as it fires. The app asks permission to notify
only when you actually turn a notification on, not before. And the settings gear, which used to live
only on the home screen, now sits in the corner of every tab, so settings is always one tap away.

### milestone: a memory, and a way out (2026-07-17)

Two last pieces close out the spine. First, history: inside settings there is now a "history" screen
that lists everything you have ever finished, newest first, gathered under day headings like "today"
and "yesterday", each with the time you ticked it off. Because a finished task's row is cleared away
each morning, this list reads from a separate permanent record that is written the moment you complete
something, and it now remembers which project the task belonged to, even if you later rename or remove
that project.

Second, backup. You can now export everything, every task, project, and completion, into a single
file you choose the location for, and import it back later, which replaces what is in the app with
what is in the file. It uses the phone's own file picker, so the app never needs permission to roam
your storage, and the file is plain, readable JSON. The part that decides exactly how that file is
shaped is written as a small self-contained piece with its own test that saves and reloads a sample
and checks nothing was lost, so a backup made today will still open correctly later.

With capture, projects, task detail, the grouped stack, the completion ceremony, reminders, history,
and backup all in place, the deterministic spine the plan set out to build is, for daily use, done.
What remains of phase one are a few tails (a couple of finer notification and deletion behaviours),
after which comes phase two: teaching crux to understand typed shorthand, still entirely offline.

### milestone: phase 1 is complete (2026-07-17)

The last handful of pieces are in, and with them the first phase of crux is done. Reminders now fire
for individual tasks at the exact minute you set, not just as the twice-daily summaries; the app
re-arms them whenever your list changes and again after the phone restarts. The clearing-away of
yesterday's finished tasks now also happens on its own overnight, so it is not left waiting for you to
open the app. And projects you have set aside can be swept out for good from settings, which also
frees their names to be used again.

That completes the promise the plan opened with: a calm, entirely offline task app that lets you
capture a thought in one line, file it under a project you have ranked, keeps the few things that
matter today in front of you, and lets you tick them off with a small moment of ceremony, backed by
reminders, a permanent history, and a backup you own. Everything so far was built to prove this spine
before adding anything clever.

What comes next is phase two: teaching crux to read the shorthand you already type, a "#" for a
project, a "!" for a priority, a date in plain words, and to show you what it understood so you can
correct it, all still without a line of network code. The groundwork for it, a table of example
phrases and the answers they should produce, has been sitting in the tests since the very first
milestone, waiting. That is where the next chapter begins.

## 5. how this journal works

- one section per milestone, newest at the bottom of section 4.
- updated the moment a milestone lands, before anything else.
- committed to GitHub with each update, so the build's story is versioned alongside the code.
- the day to day task tracking lives in `TASKS.md`; this file is the readable narrative.
