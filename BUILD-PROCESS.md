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

- **phase 0, foundation.** the empty skeleton that builds and runs.
- **phase 1, the spine (no AI).** real capture, projects, the task list, ticking, notifications,
  backup. daily-drivable for two weeks before anything else.
- **phase 2, deterministic intelligence.** typed shortcuts like `#project`, `!p1`, dates, and
  recurrence, all offline, no AI.
- **phase 3, the AI layer.** natural language parsing with a visible, correctable result.
  (this is where we are, 2026-07-18.)
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

### milestone: phase 2, crux learns to read your shorthand (2026-07-18)

Phase two taught crux to understand the shorthand you already type, still with no network anywhere.
Type "sov deck tomorrow 2pm #growbydata !p1" and the app pulls it apart as you write: it finds the
project after the "#", the priority after the "!", and the date and time in plain words, and shows
each thing it found as a small neutral chip above the box. If it reads one wrong, you tap that chip to
dismiss it and those words simply fall back into the title, no retyping. An unfamiliar "#tag" offers
to become a new project right there on capture. All of this is one small, pure piece of logic with no
knowledge of the phone or the screen, so it is covered by the table of example phrases that had been
waiting in the tests since the very first milestone. Numeric slash dates like "5/1" are deliberately
left alone, because "may the first" and "the fifth of january" cannot be told apart and guessing wrong
would quietly move your task.

Three more things arrived with it. A repeating task now spawns its next copy the moment you finish the
current one, rolling forward without ever piling up a backlog. The "N overdue" count on home became a
tappable pile showing just the past-due tasks. And the stack tab gained a "week" view, a calm
seven-day agenda beside the grouped list. Plus two the owner asked for: home can show any number of
tasks from one to ten, not only three, and the capture bar now slides out of the way as you scroll
down through a long list and springs back as you scroll up. With this, everything the app does is
still entirely offline. The next chapter is the first that is not.

### milestone: phase 3, the optional AI layer arrives (2026-07-18)

This is the big one, and the first time crux talks to the internet at all, always because you asked
it to, never on its own. Settings gained an "intelligence" section with an "ai assist" switch, off by
default. Turn it on and the app asks for a key: you choose a provider and paste your own, and it is
stored encrypted on the phone, never in the code and never shipped with the app. With it on, two new
things become possible.

First, the model fills the gaps your shorthand left. If you did not name a project but it can tell
which one a task belongs to, it files it there, only ever a project you already have, never one it
invents, and it marks anything it touched as its own guess. Your own words are always kept as the
title; the model never rewrites what you wrote. Second, command mode: you can type "tick the sov
deck", "push the vendor call to friday", or "what is due today", and the app does it, matching the
task against your own list on the phone so a wrong guess can never touch the wrong thing.

The rule that shaped the entire layer: with AI switched off, nothing changes. The deterministic core
still runs first and always; the model only ever adds to what the rules already found, and if a call
fails for any reason the app carries on with the rules alone. A new "review" tab puts the model to
work on the inbox: ask it to sort, and it proposes a home for each unfiled task with a one-line
reason, which you accept or wave off, one tap at a time.

### milestone: finding a key that actually works, and the AI light (2026-07-18)

The plan had assumed a free Google (Gemini) key. Reality was messier, and it is worth writing down.
My ChatGPT key had no credit on it. My freshly-made Gemini key turned out to be shut out of the free
tier in my region unless I attach a card, which I did not want to do. The fix was to add a third
choice, OpenRouter, whose free models genuinely work with no card at all, and to make the app tougher:
free models come and go and get busy, so the app now keeps a short ordered list of them and quietly
steps to the next one if any is unavailable. With an OpenRouter key pasted in, the whole chain worked
end to end for the first time, and "tick water plants" ticked it off.

Alongside the providers, a small light. Next to the settings gear now sits a mark shaped like the
app's own red bloom, not a generic robot or a sparkle. It sits quiet grey with a line struck through
it when AI is off, glows a steady ember when it is on, and breathes softly while it is thinking. When
a call fails, a short calm message drifts out of it saying what went wrong, out of quota, rate
limited, offline, and that the app has fallen back to its rules. Tapping the light jumps straight to
the AI part of settings.

### milestone: a queue, so capture never waits (2026-07-18)

The one rough edge of the AI layer was speed. A model call takes a second or two, and when you have
five things to empty out of your head, waiting between each one breaks the flow. So capture stopped
waiting. The instant you fire a line, it flies up into a new queue mark beside the AI light, a small
chip that shoots into place, and a worker behind the scenes handles it while you type the next. Fire
ten in a row and they line up and get done one at a time. Tap the queue mark to see the list, each
line with its state: queued, working, added, or, if a command could not be matched to a task, a plain
red note of why. Swipe a line left to drop it, right to try it again. And if you close the app while
the queue is still going, the work is remembered and finishes anyway.

Two smaller fixes rode along in the same stretch. The AI light is now tappable, and it lands you on
the AI settings already scrolled into view. And a nagging navigation bug is gone: tapping a tab while
you were deep in a pushed screen like settings used to do nothing; now it takes you to that tab, as it
always should have.

A note on how these were tested, since phase three is the first part that can fail in ways the phone
cannot show on its own: because the app quietly falls back to its rules whenever a call does not work,
a broken key or a bad address looks exactly like everything being fine. So each step was checked by
reading the phone's own live log while driving the app, which is how the quota and region problems
above were found and named rather than guessed at. Some temporary logging is still in place for that
reason and will be taken out before any wider release.

### milestone: the app learns to breathe, and matches its own drawings (2026-07-18)

The last big piece of phase four is not a feature you use, it is the way the whole thing moves and
sits. Two jobs, done together.

The first is motion. The brand book always promised exactly two loops in the entire app, and both
belong to the bloom, the soft red glow behind the capture bar on the home screen. Until now that glow
was painted once and held still. Now it breathes: at rest its light drifts up and down by a few
percent over six slow seconds, and the moment you hold the mic to speak it draws in tighter and
quickens to match the act of talking. Lists learned a smaller motion too. The first time you open the
stack in a session, the rows arrive in a gentle downhill cascade, top one first, each a beat behind
the last, then settle. It plays once and never nags you again on a tab you return to, and if you have
turned system animations off, it respectfully does nothing but the plain fade. The re-order settle, the
soft spring when a row changes place, was already correct from earlier work, so it was left alone.

The second job was honesty with the original drawings. Early in the project a designer drew all eight
screens as static mockups, and over months of building, the real screens had drifted from them in
small ways: a title missing its little eyebrow line and its running count, group headers set in the
wrong typeface, project ranks shown as plain numbers instead of the single filled garnet chip the
design reserves for rank one, the task detail page stacking its fields vertically instead of the neat
two-column rows the mockup laid out. So every screen was put side by side with its drawing and the gaps
were closed, one by one, until a screenshot of each surface reads like the mockup it came from. A few
pieces were deliberately left for later because building them now would either mislead or balloon the
work: a search toggle with nothing behind it would be a lie, so it waits for real search; and a couple
of buttons the mockups show, like carrying the whole overdue pile to today, need new plumbing under the
hood before they can do what they promise, so they are noted and parked rather than faked.

Everything here was checked the usual way, installed on the phone and walked through screen by screen
before handing back. (The temporary logging mentioned in the phase-three note above has since been
removed, as promised, once the AI paths were trusted.)

## 5. how this journal works

- one section per milestone, newest at the bottom of section 4.
- updated the moment a milestone lands, before anything else.
- committed to GitHub with each update, so the build's story is versioned alongside the code.
- the day to day task tracking lives in `TASKS.md`; this file is the readable narrative.
