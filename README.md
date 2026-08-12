# T9 QWERTY Keyboard

An Android system keyboard based on the supplied layout: number keys carry QWERTY letter groups (`1=QWE`, `2=RTU`, …, `9=NM`). In word-search mode one number per group filters suggestions; exact completed words are sorted first. For example, `435397 35 86486 124227 62451 93 831` produces “sphinx of black quartz judge my vow”. (The original brief’s final digits for `quartz` and `my` conflict with its printed key layout; this project follows the layout.)

Tap the lock button to switch to a **normal separated-letter keyboard**. In that mode, tap `h` then `i` directly. Tap LOCK again to return to T9 word search.

The app bundles an offline American-English word list generated from [ESDB / SCOWL](https://github.com/en-wl/wordlist), including its vetted inflections and spelling information. See `app/src/main/assets/DICTIONARY_LICENSE.txt` for source and license details.

## Build and install

Follow [SETUP.txt](SETUP.txt). This is a native Android IME because an Expo/React Native app cannot register as Android’s system keyboard by itself.
