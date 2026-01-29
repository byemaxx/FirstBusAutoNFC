# First Bus AutoNFC

An xposed module that temporarily disables NFC when the First Bus (UK) app displays the QR ticket to reduce the chance of accidental contactless payments.

## Features

- Detects when First Bus displays the QR ticket.
- Temporarily turns NFC off while the ticket view is displayed.
- Restores NFC when the ticket view is closed.

## Requirements

- LSPosed (recommended) or another compatible Xposed framework.
- Root access on the device (required to toggle NFC programmatically).

## How it works

- Hooks First Bus via LSPosed/Xposed to detect the QR ticket display UI or related behavior.
- Sends a request from the hook to the module app to toggle NFC state.
- NFC is toggled using `su` (root) to ensure system-level control.

## Build

To build the debug APK:

```
./gradlew :app:assembleDebug
```

## Install & Enable

1. Build and install the APK on your device.
2. In LSPosed, enable the module and set its scope to the First Bus app.
3. Force-stop First Bus and reopen it (or reboot the device) to apply hooks.

## Notes & Safety

- Use this module at your own risk. It aims to reduce accidental payments but cannot guarantee prevention.

## Troubleshooting

- No effect: verify LSPosed scope includes First Bus, then force-stop and reopen the app.
- Root permission prompt: grant root access to the module app in your root manager.
