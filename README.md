# Substitute Dashboard (Android)

Reads the public Google Sheet "LNHS-SHS-SUBSTITUTION-FORM (Responses)" and shows the
rows for the current date: NAME, DATE, TIMESLOT, SUBJECT, SECTION, SUBSTITUTE.
(The form's "Timestamp" column is ignored.)

## Get the APK
Option A - Android Studio: open this folder, let Gradle sync, then
  Build > Build App Bundle(s) / APK(s) > Build APK(s).
  Output: app/build/outputs/apk/debug/app-debug.apk

Option B - no install, GitHub Actions: create a GitHub repo, upload this folder's contents,
  open Actions > "Build APK" > Run workflow. Download "SubstituteDashboard-APK" when done.

Install the APK on the phone (allow "install unknown apps" for your file manager/browser).

## Change the sheet
Tap the gear icon in the app and paste another public sheet link.
