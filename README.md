# First Bus AutoNFC

A module that reduces accidental contactless payment prompts while the First Bus (UK) app shows the QR ticket screen.

## Features

- Detects when First Bus displays the QR ticket screen.
- While the ticket screen is visible:
	- With Root (recommended): turns NFC fully OFF.
	- Without Root: enables foreground NFC ReaderMode as a best-effort mitigation.
- When leaving the ticket screen: restores the previous NFC state / disables ReaderMode.

## Requirements

- LSPosed (recommended) / Xposed, or LSPatch.
- Root access is optional:
	- With Root: can turn NFC fully OFF/ON during ticket display.
	- Without Root: uses ReaderMode as a fallback.
	    > **NOTE:** ReaderMode is NOT the same as turning NFC off. It may not block all payment prompts on all devices.

## How it works

- Hooks the First Bus ticket Activity lifecycle to know when the ticket screen enters/leaves foreground.
- Uses a user-selected strategy:
	- Root mode: the module app runs `su -c` to toggle NFC at the system level.
	- ReaderMode: enables `NfcAdapter.enableReaderMode(...)` for the ticket Activity.

## UI notes

- The "Activated" status on the home page is based on the Xposed/LSPosed activation API and may show as not activated under LSPatch even when the hook is working.

## Usage

1. Open the module app.
2. Choose the strategy:
	- **Use Root mode (turn NFC OFF)**: strongest effect, requires granting Root to the module app.
	- Off: ReaderMode fallback.
3. Open First Bus and enter the QR ticket screen.
4. A toast should appear indicating the NFC state change.

## Build

To build the debug APK:

```
./gradlew :app:assembleDebug
```

Windows:

```
./gradlew.bat :app:assembleDebug
```

## Install & Enable

1. Build and install the APK on your device.
2. Enable hooking:
	- **LSPosed/Xposed**: enable the module and set its scope to the First Bus app.
	- **LSPatch**: patch the First Bus APK with this module and install the patched APK.
3. Force-stop First Bus and reopen it (or reboot the device) to apply hooks.

## Notes & Safety

- Use this module at your own risk. It aims to reduce accidental payments but cannot guarantee prevention.

## Troubleshooting

- No effect: verify LSPosed scope includes First Bus, then force-stop and reopen the app.
- Module not activated: this status may be unreliable under LSPatch; verify by entering/leaving the ticket screen and observing the toast.
