# Alarmify

An alarm app for Android (built for Pixel), in Kotlin + Jetpack Compose.

- **Alarms**: a hero card counting down to your next alarm, painted in that hour's sky; a wheel time picker;
  repeat days with Weekdays / Weekends / Daily shortcuts; labels; swipe to delete with undo.
- **Quick**: drag a dial (one lap = an hour, up to 3), tap a preset (5m to 1h 30), or tap a "wake up at" time.
  Running quick alarms count down live and cancel with one tap.
- **Ringing**: full screen over the lock screen, sound fades in over 20 seconds, a big Snooze button, and a
  deliberate slide to stop so you don't switch it off in your sleep. Silences itself after 10 minutes.

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
