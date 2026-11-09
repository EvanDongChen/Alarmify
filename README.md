# Alarmify

An alarm app for Android (built for Pixel), in Kotlin + Jetpack Compose.

One screen, in dark mode. At the top, a **24-hour day ring** laid out like the sun's path (noon at the top,
midnight at the bottom): a live dot for now, every alarm at its time, and an arc sweeping from now to the next
alarm with the countdown in the middle. Tap the ring to make an alarm at that time; tap a marker to edit it.
Below it, every alarm in a single list, with **New alarm** and **Quick alarm** at the bottom.

- **New alarm**: wheel time picker, repeat days with Once / Weekdays / Weekends / Daily, a label, vibrate.
- **Quick alarm**: a popup with six big buttons (1, 5, 10, 15, 30 min, 1 hour), each showing when it would ring.
  One tap sets it and it shows in the list as a one-time alarm, ringing to the second.
- **Ringing**: full screen over the lock screen, sound fades in over 20 seconds. With headphones connected (wired,
  USB or Bluetooth) it rings only in them, otherwise out loud; it switches over if you plug in or unplug mid-ring.
  A big Snooze button and a
  slide to stop. Silences itself after 10 minutes.
- Swipe an alarm left to delete it (with undo). One-time alarms switch off after ringing and stay in the list.

The look: deep ocean. Dark teal water, seafoam ink and one coral accent; a little line-drawn fish swims round
the day ring as "now", alarms sit on it as bubbles, and a fish bobs in the ripples when it rings. Behind everything: light rays from the
surface, drifting marine snow, kelp swaying along the bottom and a small school of fish crossing now and then.
Dividers are gentle waves, the popups have a wavy top edge, and bubbles pop out when you set a quick alarm or
switch one on. Instrument Serif for times and Instrument Sans for text
(both SIL Open Font License, bundled in `app/src/main/res/font`).

Alarms use `AlarmManager.setAlarmClock`, so they ring exactly on time in Doze, and are restored after a reboot.

## Install on your phone

1. On the phone: Settings → About phone → tap **Build number** 7 times, then Settings → System → Developer
   options → turn on **USB debugging**.
2. Plug it in and allow the computer when asked.
3. Build and install:

   ```sh
   # JAVA_HOME = Android Studio's bundled JDK, e.g. "C:\Program Files\Android\Android Studio\jbr"
   ./gradlew installRelease
   ```

   Or copy `app/build/outputs/apk/release/app-release.apk` to the phone and open it.

On first launch, allow notifications. If a red card appears on the Alarms screen, tap **Allow**: it opens the
setting the app needs to ring over the lock screen.
