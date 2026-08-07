# Saarthi AI

A voice-first accessibility layer for Android, built around Indian languages. Android, Kotlin, native, Jetpack Compose.

## What's here

The interface layer: onboarding, home, history, conversation threads, settings, the
translucent assist overlay, the hand-back safety screen, and the system
assistant-slot integration (long-press home / default-assistant). Every screen
renders and every button is wired to real local state and navigation.

The screen-reading, task-automation, and voice-recognition/voice-output logic
are not implemented yet — those are next.

## Structure

- `ui/theme/` — design tokens (color, type, spacing), ported as a single
  editorial paper-ground palette.
- `ui/components/` — shared building blocks (buttons, switch, text field,
  chips, pills, the voice-state mark).
- `chat/` — the local conversation/history data model and its on-device store.
- `speech/` — language and speaker preference data (display-only; no audio
  I/O yet).
- `ui/screens/` — Home, History, Thread Detail, Settings, the onboarding
  flow, the assist overlay, and the hand-back screen.
- `ui/` — activities, navigation, and the voice-interaction-session
  scaffolding for the system assistant slot.
