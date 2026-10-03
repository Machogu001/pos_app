# BreMac360 POS Android

BreMac360 POS combines native Android screens with the full website POS and
administrator pages embedded inside the app. Native screens use the Mobile API
v1 at `{server}/api/mobile/v1` over HTTPS with Laravel Passport bearer tokens.
Embedded pages use a separate website session established by a single-use
sign-in link, without asking for the password and OTP again.

App repository: [Machogu001/pos_app](https://github.com/Machogu001/pos_app).
Backend repository: [Machogu001/Pos](https://github.com/Machogu001/Pos).
See the backend's [system mobile guide and API contract](https://github.com/Machogu001/Pos/blob/main/docs/MOBILE_API.md)
and [installation runbook](https://github.com/Machogu001/Pos/blob/main/INSTALLATION.md).

Current app version: **2.11.0** (Android version code **15**). Android **8.0
(API 26)** or newer and an internet connection are required. This app does not
provide offline sales or queued offline synchronization.

## Features

- Saved server URL with Edit, password visibility toggle, OTP verification/resend,
  encrypted Android Keystore token storage, sign out and session-expiry handling.
- Home with the user's name, permitted location selection, Today/Week/Month/Custom
  performance ranges, totals, recent sales and register actions.
- Native Quick sale with product search, barcode/SKU lookup for keyboard-wedge
  scanners, stock warnings, cart quantities, permitted price edits/discounts,
  customer search/create, split payments, cash/card/M-Pesa STK polling, drafts,
  quotations, final sales and idempotent client references.
- Receipt view with PDF invoice/draft/quotation opening and document attachment sharing.
- Sales history with status filters, search, pagination and sale detail.
- Permission-filtered navigation drawer, embedded website POS, and administrator
  website menus including available purchases, products and reports.
- Light, Dark or Use device theme; pull-to-refresh on supported screens and
  horizontal back/forward navigation.

## Sign in and navigation

1. Enter the POS website base URL and tap **Save**. The address editor is hidden
   after saving, including after sign-out; use **Edit** to change the server.
   Saving an address does not itself confirm server availability.
2. Enter the existing system username and password. Tap the eye icon to reveal
   or hide the password.
3. When OTP is enabled, enter the six-digit code. The app submits automatically
   once six digits are entered; **Verify and continue** is also available.
4. For SMS delivery, compatible Google Play services may show a prompt asking
   to read that one message. Choose **Allow** to fill and submit the code.
   This does not grant general SMS inbox access. Email codes, declined consent,
   unsupported devices and missed SMS messages can be handled by manual entry.
5. Use the top-left menu on drawer screens to open available features, change
   location, select **Appearance**, or sign out.

Resend by SMS or email is available after the server's cooldown and requires a
valid delivery target on the account. Expired OTP challenges require signing in
again. A theme change returns to Home and is remembered across sign-out.

Pull down to refresh on screens that support it. Swipe left-to-right to go back;
right-to-left reopens the last eligible screen closed with Back. Embedded pages
use their own page history. Edge gestures and horizontally scrolling tables
are excluded from app swipe navigation.

## Access and screen types

| Area | Screen type | Access |
|------|-------------|--------|
| Home performance | Native API | Dashboard permission |
| Quick sale | Native API | Sell/create or direct-sale permission |
| Sales history | Native API | Sales-view or own-sales permission |
| Products & stock | Native API | Product-view permission |
| Customers | Native API | Customer-view or own-customer permission |
| Cash register | Native API | Role-based register actions; closing requires close-register permission |
| POS | Embedded website | Sell/create or direct-sale permission |
| Business system menus | Embedded website | Business admin; website permissions and enabled modules still apply |

The server enforces permissions independently of menu visibility. Admin website
features are available through the drawer rather than a separate Full system
button. Native Quick sale is not a replacement for every full website feature.
Embedded pages hide the website's duplicate navigation while retaining its
forms, filters, exports and print flow. Their appearance depends on the website
and the phone's WebView.

## Business locations

Use **Change location** in the app menu or the **LOCATION** selector on Home.
Users only see locations granted by their system permissions. **All locations**
combines performance, recent sales and sales history across those permitted
locations, and remembers the selection until sign-out.

Quick sales, products/stock, payments and cash registers still use the last
selected individual location, shown in their app bar. Selecting another branch
from the menu asks before clearing an unfinished quick-sale cart. Quick sale's
**SELLING FROM** selector selects an individual branch and reprices the cart.
The embedded website pages retain their own location filters.

## Server requirement

Deploy the BreMac360 backend with Mobile API v1 enabled over HTTPS. Laravel
Passport keys and a personal-access client must be configured for authenticated
API requests. Cleartext HTTP is disabled in the Android manifest.

Deploy the compatible backend before distributing a new app version. Configure
OTP delivery, allowed business locations, roles, subscription access and payment
methods on the server. M-Pesa additionally needs valid sell-payment credentials
and working server callback handling, separately from subscription payments.

## Server URL entry

Enter the POS website base URL, for example `https://example.com`
or `https://example.com/retail` when the backend lives in a subdirectory.
The app normalizes a pasted full Mobile API URL such as
`https://example.com/api/mobile/v1` back to the website base URL.

A valid trusted TLS certificate is required; do not disable certificate
validation to work around a server configuration problem.

## Activity log

The app identifies its version and Android maker/model to the server.
Authentication activity entries distinguish the native mobile app, the in-app
website and a web browser. Review these in **Reports > Activity log** with an
authorized account. Device labels are descriptive metadata, not a trusted
device identity or proof of ownership.

## Receipts and document sharing

After saving, the receipt contains the saved sale's details and payment
references. **Open invoice** (or Open draft/Open quotation) retrieves the
server-generated PDF and opens it inside the app with page and zoom controls. **Share** attaches
that PDF rather than sending a plain-text receipt. Sales history offers the same
document actions for existing sales; neither action creates a payment.

Deploy the backend's `GET /sales/{id}/document` endpoint before installing this
version. It reuses the system's invoice PDF renderer and respects business,
location and own-sale access. PDF rendering needs the backend's existing mPDF
dependencies and writable `public/uploads/temp`. PDFs are cached in the app's
private cache and shared through temporary read grants, without exposing tokens.
Only share customer/payment documents with intended recipients.

## Stock protection

Quick sale shows available branch stock. Repeated product additions, the +
button and manually entered quantities cannot exceed the known stock; tap
the quantity to enter a decimal amount. Stock-tracked products with unknown
availability cannot proceed until stock is refreshed. Non-stock-tracked
services are not quantity-limited by inventory.

The app checks current server stock before opening checkout, accepting a
payment, sending an STK request and completing a sale. Insufficient stock
shows a warning and prevents continuation. The backend checks again while
saving, aggregates duplicate variation quantities and shared combo components,
and holds stock locks during the mobile save. Completed mobile sales do not
honor the website's Allow overselling setting; drafts and quotations do not
consume stock. The website's own overselling policy is unchanged.

Stock can change after an STK request was sent. A stock failure at completion
must not trigger a second payment: the confirmed payment remains in the cart.
Resolve stock or the payment with the administrator before retrying completion.
Pre-payment checks are not stock reservations.

## Thermal receipt printing

Use **Print receipt** on the completed receipt, sale details or in-app document
viewer. This prints a thermal receipt from the saved document's sale data;
it does not send A4 PDF bytes to the printer.

Supported printers must implement **ESC/POS** using Bluetooth Classic serial
printing (SPP), or raw network TCP printing (commonly port **9100**). BLE-only,
USB-only and vendor-specific non-ESC/POS printers are not supported.

Pair Bluetooth printers through Android Bluetooth settings first, then select
a paired printer in the app. Android 12+ requires the app's Nearby devices /
Bluetooth connection permission. No discovery/location permission is requested.
For network printing, enter the printer's reachable IP/host and TCP port; the
phone and printer normally need the same trusted local network.

Select the matching 58 mm or 80 mm paper width. Printer settings are stored
locally. ESC/POS output uses a basic ASCII-safe text receipt; it is not a
pixel-identical copy of the website PDF and non-ASCII artwork/text may differ.
Successful transmission means data was sent, not that paper output was
confirmed. If a connection fails midway, check the printer before retrying to
avoid duplicate receipts.

## Troubleshooting

| Symptom | What to check |
|---------|---------------|
| Cannot connect after saving URL | Website base URL, HTTPS certificate, connectivity and deployed mobile routes |
| OTP is not received | Account phone/email, SMS/email delivery configuration and resend cooldown |
| SMS does not auto-fill | Google Play services and consent prompt; enter the code manually |
| Session expires immediately after OTP | Passport keys/personal-access client and Authorization forwarding through the web server/proxy |
| Unable to continue / server error | Backend logs, deployed API version and required configuration; do not repeatedly retry a payment blindly |
| Feature or location is missing | Account permissions, permitted active locations and enabled modules |
| Wrong branch totals | Home/menu location filter; embedded website filters are independent |
| Register closed | Open the required register before completing a sale |

Only administrators should inspect server logs. Never send passwords, OTPs,
tokens, M-Pesa credentials or signing passwords in support messages.

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
These Windows paths describe the current development workspace; adjust them
for another workstation's checkout, JDK and SDK.

## Release build and maintenance

Signed builds need `keystore.properties` in the project root (never committed).
Set `storeFile` to the private keystore path and configure `storePassword`,
`keyAlias` and `keyPassword` using securely stored credentials.

With the Java/Android environment configured as above, run from the app root:

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease bundleRelease --no-daemon
```

Outputs are `app\build\outputs\apk\release\app-release.apk` and
`app\build\outputs\bundle\release\app-release.aab`. The APK can be distributed for
direct installation; the AAB is for store upload, not direct phone installation.

Increment `versionCode` for each distributed update and update `versionName`.
Sign with the existing release key so installed apps can be upgraded. Verify
the APK signature with Android SDK `apksigner` before distribution.

In the current Windows workspace, private signing material is kept outside the
repository in `D:\Myapps\pos_app_keys`; versioned release artifacts are kept in
its `releases` directory (for example, `BreMac360-POS-2.11.0.apk` and `.aab`).
These local files are not automatically GitHub Releases or store publications.
Keep encrypted, access-controlled backups of the keystore and recovery
credentials. Never commit keystores, `keystore.properties`, passwords or private
keys. Distribute signed production builds, not debug builds.

Server updates, PWA installation and the server's remote-version notifications
do not automatically update the native Android app.
