# Changelog

All notable changes to T9 QWERTY Keyboard are recorded here.

## 1.2.1 — 2026-08-13

- Fixed the standard QWERTY suggestion strip so predictions are visible while typing.
- Reduced key handling overhead in standard mode to improve responsiveness.
- Added closest T9-code fallback candidates that favour similar-length words matching most entered groups.
- Enabled same-code alternatives for explicitly selected words in either keyboard mode.
- Made Backspace delete an active text selection in one action.
- Made punctuation remove a preceding space, attach to the previous word, then add one following space.

## 1.2.0 — 2026-08-13

- Added labelled, more visible T9, prediction, and same-code suggestion states.
- Added standard QWERTY keyboard predictions, including typo-tolerant fallback suggestions.
- Added British spellings, common conversational words, and popular acronyms to the local dictionary.
- Ensured T9 search always falls back to useful common suggestions instead of showing an empty strip.
- Added an automatic numeric keypad for number, phone, date, and time fields.

## 1.1.4 — 2026-08-12

- Corrected hidden-number long press in normal QWERTY mode to enter the displayed number rather than the letter.

## 1.1.3 — 2026-08-12

- Fixed long-press number entry on the normal QWERTY top row when the number row is hidden.

## 1.1.2 — 2026-08-12

- Added visible, light-number hints and long-press number entry when the number row is hidden.
- Made the normal QWERTY top row map to `1`–`0` on long press when numbers are hidden.
- Removed duplicate punctuation and Return keys from compact mode.
- Added the app version and a link to update notes in Settings.
- Adopted versioned APK release filenames.

## Unreleased

- Added a Settings shortcut for filing a GitHub bug report with device and keyboard-setting details prefilled.

## 1.1.1 — 2026-08-12

- Made compact mode a uniform 3×3 T9 grid with separate controls.
- Added long-press number entry on T9 letter groups when the number row is hidden.
- Applied navigation-bar safe-area handling to T9, normal QWERTY, and punctuation layouts.

## 1.1.0 — 2026-08-12

- Added optional number-row visibility to create more vertical screen space.
- Added optional auto-space after selecting a word suggestion.
- Added an optional compact, one-handed keyboard layout with LHS, centre, and RHS placement.
- Reserved bottom safe-area space so Android navigation controls do not cover the keyboard.
- Added a settings screen for restoring removed suggestions, theme, size, and typing preferences.

## 1.0.0 — 2026-08-11

- Initial T9 QWERTY Keyboard release.
- Offline ESDB / SCOWL English dictionary with ranked suggestions.
- Connected T9 and separated-key QWERTY layouts.
- Punctuation, caps lock, repeat backspace, adjustable size, and dark theme.
