# First Bus AutoNFC

A module that reduces accidental contactless payment while the First Bus (UK) app shows the QR ticket screen.

## Features

- Detects when First Bus displays the QR ticket screen with improved lifecycle detection (Instrumentation hooks).
- Three protection strategies while the ticket screen is visible:
	- **Mode 1: Root mode (Recommended)**: Turns NFC fully OFF at the system level.
	- **Mode 2: Reader Mode (Standard)**: Enables NFC ReaderMode for the ticket Activity as a best-effort mitigation.
	- **Mode 3: Jump to NFC Settings**: Prompts the user to manage NFC in Settings when the ticket screen is active.
- When leaving the ticket screen: restores the previous NFC state / disables applicable protections.

## Requirements

- LSPosed (recommended) or LSPatch.
- Root access is optional but recommended:
	- **With Root**: Enables Mode 1 (Root mode) for the strongest NFC protection.
	- **Without Root**: You can still use Mode 2 (Reader Mode) or Mode 3 (Jump Settings).

## How it works

- Hooks the First Bus ticket Activity lifecycle to know when the ticket screen enters/leaves foreground.
- Also hooks Instrumentation callbacks and BottomBarHostActivity for more reliable detection across different UI transitions.
- Uses a user-selected strategy:
	- **Root mode**: The module app runs `su -c` to toggle NFC at the system level.
	- **Reader Mode**: Enables `NfcAdapter.enableReaderMode(...)` for the ticket Activity.
	- **Jump Settings**: Prompts the user to open NFC settings in the system Settings app when they enter or exit the ticket screen.


## Usage

1. Open the module app.
2. Choose your preferred protection strategy:
	- **Mode 1: Root mode (Recommended)**: Strongest effect, requires granting Root permission to the module app. Turns NFC fully OFF while viewing the ticket.
	- **Mode 2: Reader Mode (Standard)**: Uses NFC ReaderMode as a fallback. Does not turn NFC off but prevents most payment prompts.
	- **Mode 3: Jump to NFC Settings**: Prompts you to open NFC settings when you enter or exit the ticket screen, allowing manual control.
3. Open First Bus and enter the QR ticket screen.
4. A toast should appear indicating the action taken by your chosen strategy.
5. When you exit the ticket screen, the strategy will be reversed (for Root/Reader modes) or prompt will be offered (for Jump Settings mode).

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
- Module not activated: this status may be unreliable under LSPatch; verify by entering/leaving the ticket screen and observing the toast or prompt.
- Root mode not working: ensure you granted Root permission to the module app. The app will fall back to Reader Mode if Root is unavailable.
- Jump Settings not prompting: make sure the strategy is set to Mode 3, and ensure the app has proper permissions to launch Settings.
