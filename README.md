# First Bus AutoNFC

Automatically turns **NFC off** when **First Bus** opens the QR-code scanning flow, to prevent **NFC Pay** from being triggered and causing accidental double payment.

## What it does

- Detects when First Bus enters the QR/scan flow
- Turns NFC off during that flow to reduce the chance of unintended NFC payments

## Requirements

- Android 8.1+ (`minSdk 27`)
- LSPosed (recommended) / compatible Xposed framework
- Root is required for toggling NFC programmatically (most ROMs)

## How it works
- Hook First Bus (via LSPosed/Xposed) and detect the scan-related UI/behavior
- Request the module app side to toggle NFC
- NFC toggling is done through `su` (root)

## Build

- Debug: `./gradlew :app:assembleDebug`

## Install / Enable

1. Build APK: `./gradlew :app:assembleDebug`
2. Install the APK on your device
3. In LSPosed, enable the module and set the scope to **First Bus**
4. Force-stop and reopen First Bus (reboot if needed)

## Notes

- NFC toggling is a sensitive operation; behavior may vary by ROM/device.
- Use at your own risk; this project aims to reduce accidental payments, not guarantee prevention.

## Troubleshooting

- No effect: make sure the LSPosed scope is set to First Bus, then force-stop First Bus and reopen
- Root dialog/permission: grant root to the module app in your root manager
