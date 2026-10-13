# Glass SMS

An Android SMS app with an iOS-style "liquid glass" interface, NYC Subway typography, and
on-device one-time-code (OTP) handling.

## Features
- **Default SMS app**: receives, sends and stores SMS; handles `sms:`/`smsto:` links and "respond via message".
- **Liquid glass UI**: floating blurred-glass bars and buttons (Haze, RenderEffect on Android 12+),
  specular rim highlights, jelly press animations, a liquid segmented control and scroll-edge blur.
- **Subway styling**: one font slot (`SubwayFont` in `ui/theme/Theme.kt`), sign-style rule above
  large titles, avatars drawn as MTA route bullets.
- **OTP management**:
  - Detects codes on-device (`otp/OtpParser.kt`); covers "code is 1234", "G-123456", "123-456"
    and skips amounts, phone numbers and promo codes.
  - Notification shows the code with a **Copy** button; the full code is hidden on the lock screen.
  - Optional auto-copy on arrival; clipboard entry marked sensitive.
  - Optional **delete after copying** (1 min to 1 hr, via WorkManager).
  - **Codes** screen: last 24 hours of codes, live expiry ring, tap to copy, bulk-delete expired.
  - Codes highlighted inside the conversation, with a copy chip.

## Build
Open the folder in Android Studio (Narwhal or newer) and run. Requires JDK 17+, compileSdk 36, minSdk 26.
Run the OTP parser tests from Android Studio: right-click `OtpParserTest` and choose Run.

## Font
The MTA signs use Helvetica, which is licensed. The app ships **TeX Gyre Heros** instead, a free
Helvetica clone (GUST Font License, a LaTeX Project Public License variant; see `licenses/`).
The files are renamed to `res/font/subway_*.otf`, which matches the license's request that
redistributed copies get new names. To use real Helvetica, replace those files with your
licensed copies; `SubwayFont` in `ui/theme/Theme.kt` picks them up.

## Not yet
MMS (receiver is a stub), group threads, dual-SIM picker, reactions, contact picker.

## Play Store note
SMS permissions are restricted on Google Play. Only apps whose core function is SMS, and that are
the default handler, are approved, and a permissions declaration form is required.
