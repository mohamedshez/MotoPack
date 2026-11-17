# MotoPack roadmap

A running record of what MotoPack does today and what comes next.

**How to use this file**

- Features are built in Google AI Studio with Gemini, then synced to GitHub and pulled locally.
- Each planned feature has a ready-to-paste prompt. Run one prompt at a time in AI Studio.
- When a feature lands, tick its checklist, move it to [Implemented](#implemented) and add the date.

Last updated: 16 November 2026

## Status at a glance

| Feature | Status |
| --- | --- |
| Haptic turn-by-turn navigation cues | Planned |
| Smart battery saver | Planned (manual eco mode exists) |
| Auto-Night mode for the Rider HUD | Planned (manual night theme exists) |

## Planned

### 1. Haptic turn-by-turn navigation cues

**Goal:** riders feel navigation cues through the phone on its bike mount, so they do not need to look at the
screen.

**What exists today**

- The Rider HUD already tracks the current manoeuvre, the next one and the distance to the next turn
  (`currentManeuver`, `thenManeuver`, `distanceToNextTurnMeters` in `MotoPackViewModel.kt`).
- The `VIBRATE` permission is declared, and `MotoPackNotificationHelper.kt` already plays one vibration
  waveform for urgent alerts.
- There are no vibration patterns tied to navigation.

**Scope**

- [ ] A haptics engine with a distinct pattern per cue: turn left, turn right, U-turn, roundabout, keep
      straight, off route and arrived
- [ ] Patterns strong and long enough to feel through a handlebar mount and gloves
- [ ] A pre-warning cue at a distance and a second cue at the turn, with distances that scale with speed
- [ ] A setting to turn haptics on or off and choose intensity, saved in DataStore
- [ ] A "test patterns" screen so riders can learn the cues before riding
- [ ] Works with the screen off while ride tracking is running
- [ ] Unit tests for the manoeuvre-to-pattern mapping and trigger distances

**Prompt for Gemini**

> Integrate haptic feedback patterns (varying vibrations) for turn-by-turn navigation, allowing riders to
> receive navigation cues through their phone-mounted device without needing to look at the screen. Use a
> distinct vibration pattern for each manoeuvre (left, right, U-turn, roundabout, straight, off route,
> arrived), with a pre-warning before the turn and a second cue at the turn. Drive it from the existing Rider
> HUD navigation state in MotoPackViewModel. Add a settings toggle, an intensity option and a screen to test
> each pattern. Add unit tests.

### 2. Smart battery saver

**Goal:** the app protects the phone's battery on long rides by itself, so the rider is never left without
navigation or SOS.

**What exists today**

- A manual eco ride mode (the "BATT ECO" toggle on the map). It slows GPS fixes from every 5 seconds to every
  20, weather scans from every 5 minutes to every 10, and widens the separation alert threshold.
- The theme switches to the dark OLED palette when the battery is at 20% or below.
- Nothing turns eco mode on automatically.

**Scope** (the original request was brief, so this is a proposed definition to confirm)

- [ ] Turn eco mode on automatically below a battery threshold, and off again when charging
- [ ] Tiered behaviour, for example normal, saver below 30% and critical below 15%
- [ ] In critical tier, pause non-essential work (photo gallery sync, AI callouts, map animations) and keep
      location, SOS and navigation running
- [ ] Estimate remaining ride time on battery and warn when it is shorter than the route's remaining time
- [ ] A setting to choose automatic, always on or off, with the thresholds adjustable
- [ ] A clear indicator on the HUD and map showing which tier is active and why
- [ ] Unit tests for tier changes, including charging and unplugging

**Prompt for Gemini**

> Add a smart battery saver. Build on the existing manual eco ride mode so it switches on automatically based
> on battery level and charging state, with tiers (normal, saver below 30%, critical below 15%). In saver
> tiers reduce GPS, weather scan and sync frequency and pause non-essential work, but always keep location,
> navigation and SOS running. Estimate remaining battery time and warn when it is shorter than the remaining
> route time. Add a setting for automatic, always on or off, show the active tier on the Rider HUD and map,
> and add unit tests.

### 3. Auto-Night mode for the Rider HUD

**Goal:** the HUD shifts to deep blacks and low-intensity reds in the dark, to cut glare and eye strain when
riding at night.

**What exists today**

- Three theme modes: `AUTO`, `DAYLIGHT` and `NIGHT_HUD` (`Theme.kt`).
- `AUTO` follows the system dark theme, low battery and eco mode. It does not read the ambient light sensor.
- The night palette is amber, emerald and cyan on near-black, with no red-shifted palette.

**Scope**

- [ ] Read the ambient light sensor while the HUD is open and stop reading when it closes
- [ ] A new night palette: true black background, low-intensity reds, high contrast, checked for legibility
- [ ] Smooth the sensor readings and use separate on and off thresholds, so tunnels, street lights and
      headlights do not make the HUD flicker between themes
- [ ] Fall back to sunset and sunrise times when the device has no light sensor
- [ ] A manual override, plus an "Auto-Night" option in the existing theme setting
- [ ] Unit tests for the threshold and smoothing logic

**Prompt for Gemini**

> Implement an 'Auto-Night' mode for the Rider HUD that shifts the color palette to high-contrast deep blacks
> and low-intensity reds based on ambient light sensor data to reduce eye strain for night riding. Add it as a
> new option alongside the existing HudThemeMode values. Smooth the light sensor readings and use separate
> thresholds for switching on and off so the theme does not flicker under street lights or in tunnels. Only
> read the sensor while the HUD is visible, fall back to sunset and sunrise times when there is no light
> sensor, keep a manual override, and add unit tests.

## Implemented

Everything below is in the codebase as of the initial commit (November 2026).

### Riding and navigation

- [x] Live rider radar on Google Maps: position, speed, heading and altitude for each pack member
- [x] Separation alerts when a rider falls behind the pack
- [x] Route planner using the Google Routes API, with preset passes
- [x] Elevation and grade profile for a route
- [x] Fuel stops along a route from the Places API
- [x] Turn-by-turn step data, with a geometric fallback when the Routes API gives none
- [x] Rider HUD: a glanceable riding screen with night and daylight themes
- [x] Manual eco ride mode to save battery
- [x] Foreground ride tracking that keeps running with the screen off

### Weather

- [x] Live rain and high-wind alerts from OpenWeatherMap
- [x] Proactive weather shield: scans the route corridor for thunderstorms, heavy rain, crosswinds and ice
- [x] Weather notifications with one-tap "route to safe stop" and "detour around hazard" actions
- [x] Route corridor radar showing conditions at the start, midpoint and destination

### AI

- [x] AI copilot chat for convoy and riding advice (Gemini through Firebase AI Logic)
- [x] Structured safety briefings with a risk level and tactical advice
- [x] Smart HUD callouts with a short spoken tip
- [x] AI scenic route suggester with four riding styles, a range limit and a motorway filter

### Packs and communication

- [x] Create and join packs with an invite code (up to 30 members)
- [x] Encrypted pack telemetry and chat (AES-256-GCM)
- [x] Push-to-talk voice clips with auto-play
- [x] Urgent ring and ping alerts
- [x] Shared map pins and a pack photo gallery
- [x] Pack challenges and pack events
- [x] Emergency SOS sheet with a locale-aware emergency number
- [x] Experimental peer-to-peer convoy mesh over Wi-Fi Direct, with a mesh VPN service

### Offline

- [x] Downloadable offline map areas
- [x] Cached routes, riders, pins and trip elevations in Room
- [x] Queued GPS breadcrumbs and voice clips that sync on reconnect

### Garage, events and trips

- [x] Garage: bikes, odometer, maintenance reminders with health status and service history
- [x] Biker events with hosting, joining and routing to the venue
- [x] Biker & Brew Passport with stamps and ranks
- [x] Awards and milestones for distance and elevation
- [x] Trip summary dashboard with PDF export and social sharing

### Accounts and platform

- [x] Google Sign-In through Credential Manager
- [x] 30-minute guest preview without an account
- [x] Firestore security rules with field-level validation, plus rules tests
- [x] Firebase App Check (reCAPTCHA in release, debug provider in debug)
- [x] Layout that adapts between a bottom bar on phones and a navigation rail on wider screens

## Ideas and technical debt

Not scheduled. Promote an item to [Planned](#planned) when it is ready to be built.

- Add the Gradle wrapper, a `package.json` for the rules tests and a CI workflow
- Rename the `com.example` namespace
- Strengthen pack encryption: a proper key derivation function and keys that stay on members' devices
- Turn on code shrinking for release builds
- Split `MotoPackViewModel.kt` (about 2,800 lines) into feature view models
- Test the peer-to-peer mesh on real devices
- Add a licence
