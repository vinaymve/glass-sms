# Glass SMS

An Android SMS app with an iOS-style "liquid glass" interface, NYC Subway styling, and on-device
one-time-code (OTP) handling, built with Indian inboxes in mind.

## Install
Download the APK from [Releases](../../releases), open it on your phone and allow installing from
that source. Then open Glass SMS and tap **Set as Default SMS App**: Android only lets the default
app receive, send and delete texts. You can switch back any time in Settings → Apps → Default apps.

## Features
**Inbox**
- Liquid-glass floating bars, buttons and menus with springy press animations and scroll-edge blur.
- Smart categories: Personal, Transactions, OTPs, Offers, plus Unread and Archived. Business senders
  are sorted by their TRAI suffix (-P promotional, -S/-T/-G service, transactional, government).
- Pinned conversations as a big-avatar grid; long-press for Pin, Read/Unread, Mute, Archive,
  Block & Report Spam and Delete.
- Gmail-style swipes, configurable per direction (Read, Archive, Delete), with an optional
  8-bit explosion on delete.
- Search, inbox dates as day, date or date & time (in JetBrains Mono).

**Conversations**
- iMessage-style bubbles; tap a message to Copy, Select Text, Share, Forward or Delete.
- Tappable links, phone numbers, emails and UPI IDs. Links open through a sheet that shows the real
  domain and warns about shorteners, `.apk` downloads, raw IPs and look-alike addresses.
- ⋯ menu for the whole conversation, including Delete Conversation.
- New messages search contacts by name or number.

**Codes (OTP)**
- Detected on the phone; nothing is uploaded. Handles "code is 1234", "G-123456", "123-456" and skips
  amounts, phone numbers and promo codes.
- Notification with a Copy button (code hidden on the lock screen); optional auto-copy.
- Delete after copying, or auto-delete when expired / after 1 or 24 hours.
- Codes screen with a live expiry ring.

**Safety and housekeeping**
- Spam folder for blocked senders (prefix/suffix-insensitive), blocked words and, optionally, every
  promotional (-P) sender. Mute any conversation; offers are silent by default.
- Recently Deleted keeps deletes for 30 days, with an Undo bar after each one.
- Clean Up Inbox clears old OTPs and offers into Recently Deleted.
- Reply and Mark as Read straight from notifications.

## Build
Open the folder in Android Studio and run. compileSdk 36, minSdk 26.

Gradle runs on **JDK 21**, set in `gradle/gradle-daemon-jvm.properties`. Gradle 8.14 can't run on Java 25.
If sync says no JDK 21 was found, install one (`brew install --cask temurin@21`) and sync again.
Judge smoothness on the **release** variant; debug builds of Compose are much slower.

Release builds are currently signed with the local debug key so they install over development
builds. Use a dedicated release key before distributing to anyone else.

## Fonts
- **TeX Gyre Heros** stands in for the MTA's Helvetica (GUST Font License), as `res/font/subway_*.otf`.
- **JetBrains Mono** for timestamps (SIL Open Font License), as `res/font/mono_*.ttf`.

Both licenses are in `licenses/`.

## Not yet
MMS (picture and group messages are not received), dual-SIM picker, scam warnings, transaction cards.

## Play Store note
SMS permissions are restricted on Google Play. Only apps whose core function is SMS, and that are
the default handler, are approved, and a permissions declaration form is required.
