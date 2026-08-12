# T9 QWERTY Keyboard

T9 QWERTY Keyboard is an offline Android keyboard that combines a compact, connected-key T9 layout with an optional full QWERTY layout. It is designed for fast thumb typing while retaining familiar QWERTY letter positions.

## Features

- Connected-key T9 word search based on custom QWERTY letter groups.
- A normal, separated-key QWERTY mode, toggled with a triple tap on Space.
- Offline English word suggestions, generated from the ESDB / SCOWL word list.
- Suggestions ranked by words you use most often.
- Capitalisation, optional automatic sentence capitals, and optional double-space full stops.
- Punctuation keyboard, long-press punctuation menu, and repeat backspace.
- Adjustable keyboard size and optional dark theme.
- Suggestion management: long-press a suggestion to hide it, then restore hidden suggestions from Settings.

## Privacy

The keyboard works entirely on-device. It does not request Internet access and does not send typed text, word suggestions, or usage rankings to a server.

Android displays a standard warning when enabling any third-party keyboard because keyboards can receive the text you type. Install it only from a source you trust.

## Install

For build, installation, and tester instructions, see [SETUP.txt](SETUP.txt).

The latest test APK is available as `T9QwertyKeyboard-debug.apk` in this repository.

## Dictionary

The bundled American-English dictionary is generated from [ESDB / SCOWL](https://github.com/en-wl/wordlist). See [DICTIONARY_LICENSE.txt](app/src/main/assets/DICTIONARY_LICENSE.txt) for licensing details.

## Development

This is a native Android Input Method Editor (IME) project. Open the repository in Android Studio and use the Gradle wrapper to build it:

```powershell
.\gradlew.bat assembleDebug
```
