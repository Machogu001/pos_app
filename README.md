# BreMac POS Android

BreMac POS is now a native Java Android POS client. It no longer embeds the web
application in a WebView; the app talks directly to the BreMac POS Mobile API v1
at `{server}/api/mobile/v1` using HTTPS JSON requests and Bearer-token
authentication.

## Features

- Server URL validation, password login, OTP verification/resend, encrypted
  Android Keystore token storage, sign out, and automatic session-expiry
  handling.
- Dashboard with business/user details, location selection, Today/Week/Month
  periods, register open/close actions, totals, and recent sales.
- Native POS sale flow with debounced product search, barcode/SKU lookup for
  keyboard-wedge scanners, stock warnings, cart quantities, optional price
  edits, discounts, customer search/create, split payments, cash/card/M-Pesa STK
  polling, drafts, quotations, final sales, and idempotent client references.
- Receipt view with plain-text sharing and secure invoice opening.
- Sales history with status filters, search, pagination, and sale detail.

## Server requirement

Deploy the BreMac POS backend with Mobile API v1 enabled over HTTPS. Laravel
Passport keys/tokens must be configured because the mobile endpoints require
Bearer authentication after login. Cleartext HTTP is disabled in the Android
manifest.

## Server URL entry

Enter the POS website base URL in the app, for example `https://example.com`
or `https://example.com/retail` when the backend lives in a subdirectory.

If a user pastes the full Mobile API URL such as
`https://example.com/api/mobile/v1`, the app normalizes it back to the website
base URL automatically.

## Build

From PowerShell:

```powershell
Push-Location D:\Myapps\pos_app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\HP\AppData\Local\Android\Sdk'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
Pop-Location
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Release build

Signed builds need `keystore.properties` in the project root (never committed):

```
storeFile=D:/Myapps/pos_app_keys/bremac-pos-release.jks
storePassword=...
keyAlias=bremac-pos
keyPassword=...
```

Then run `gradlew assembleRelease` (APK) or `gradlew bundleRelease` (Play Store AAB).
Keep a backup of the keystore and its password: updates to an installed app must be signed with the same key.
