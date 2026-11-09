# Alarmify

An alarm app for Android (built for Pixel), in Kotlin + Jetpack Compose.

One screen: every alarm in a single list, with **New alarm** and **Quick alarm** at the bottom.

- **New alarm**: wheel time picker, repeat days with Once / Weekdays / Weekends / Daily, a label, vibrate.
- **Quick alarm**: a popup with a grid of durations (5 min to 2 hours), a stepper for any other length, and
  wake-up times. One tap sets it and it shows in the list as a one-time alarm, ringing to the second.
- **Ringing**: full screen over the lock screen, sound fades in over 20 seconds, a big Snooze button and a
  slide to stop. Silences itself after 10 minutes.
- Swipe an alarm left to delete it (with undo). One-time alarms switch off after ringing and stay in the list.

The look is bedside paper: cream, black ink and one red, Instrument Serif for times and Instrument Sans for text
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
