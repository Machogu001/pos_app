# BreMac POS Mobile

BreMac POS Mobile is an Android companion for the existing BreMac360 POS. It
opens the POS website in a secure, app-like workspace so staff can use the
existing sign-in, sales, inventory, and reporting screens without a second
account or a separate mobile API.

## Getting started

1. Install and deploy the BreMac POS web application as described in its
   installation guide.
2. Make sure the POS website is available over HTTPS.
3. Build and install this Android app.
4. Enter the POS website address on first launch and sign in with an existing
   POS account.

The website address is saved on the device and can be changed using **Server**
in the app header. The address must use HTTPS; credentials and session cookies
are handled by the POS website and Android WebView.

## Build

Open this project in Android Studio, or build it from the repository root:

```shell
gradlew assembleDebug
```

The project targets Android 15 and newer SDK tooling, and supports Android 8.0
(API 26) and newer devices.
