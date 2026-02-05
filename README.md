# First Bus AutoNFC

A module that reduces accidental contactless payment while the First Bus (UK) app shows the QR ticket screen.

Some bus scanners place the **QR code scanning area** and the **contactless/NFC payment area** in the same (or very close) physical position. When you present your phone to scan the QR ticket, Android may route the NFC field to the default wallet and trigger a payment prompt, which can lead to unintended/double charging. This module provides best-effort protections while the First Bus ticket screen is visible.

---

## Features

- Detects when First Bus displays the **QR ticket screen** with improved lifecycle detection:
  - Hooks the ticket Activity lifecycle.
  - Also hooks **Instrumentation callbacks** and **BottomBarHostActivity** to improve reliability across UI transitions.
- **Three protection strategies** while the ticket screen is visible:
  - **Mode 1: Root mode (Recommended)**  
    Turns NFC fully **OFF** at the system level (strongest mitigation).
  - **Mode 2: Reader Mode (Standard, Non-root)**  
    Enables Android **NFC ReaderMode** (`NfcAdapter.enableReaderMode(...)`) on the ticket Activity as a best-effort mitigation.
  - **Mode 3: Jump to NFC Settings (Non-root, Manual Control)**  
    Prompts the user to open **NFC settings** when entering/leaving the ticket screen, so the user can manually control NFC.
- When leaving the ticket screen: restores the previous NFC state / disables applicable protections.

---

## Requirements

- **LSPosed (recommended)** or **LSPatch**.
- Root access is optional but recommended:
  - **With Root**: enables **Mode 1** (strongest protection).
  - **Without Root**: you can still use **Mode 2** (Reader Mode) or **Mode 3** (Jump Settings).

---

## How it works (high level)

- Hooks the First Bus ticket Activity lifecycle to know when the ticket screen enters/leaves foreground.
- Also hooks Instrumentation callbacks and BottomBarHostActivity for more reliable detection across different UI transitions.
- Uses a user-selected strategy:
  - **Root mode**: the module app runs `su -c` to toggle NFC at the system level.
  - **Reader Mode**: enables `NfcAdapter.enableReaderMode(...)` for the ticket Activity.
  - **Jump Settings**: prompts the user to open NFC settings in the system Settings app when entering/leaving the ticket screen.

---


## Installation

### Root mode (LSPosed)
1. Install LSPosed.
2. Install this module APK.
3. Enable the module in LSPosed.
4. Select the target app (**First Bus**) in LSPosed scope.
5. Force stop the First Bus app and reopen it.

### Non-root mode (LSPatch)
1. Patch the First Bus APK with LSPatch including this module (follow your LSPatch workflow).
2. Install the patched First Bus app.
3. Open the ticket screen and verify behavior.

---


## Compatibility notes

- Behavior varies across devices and Android versions.
- Some devices/ROMs may still launch the wallet even when the app attempts to reduce NFC triggers.
- Battery optimizations and aggressive background restrictions may affect lifecycle callbacks on certain OEM ROMs.

---


## Safety / Privacy

- This project **does not collect, transmit, or store** payment and any other information.
- It **does not** intercept, replay, or modify NFC payment data (e.g., APDUs).
- It **does not** interact with POS terminals, payment networks, or ticket validation systems.
- All behavior is local to your device and limited to controlling NFC-related state/behavior while the ticket screen is in the foreground.

---

## Disclaimer / Legal

- This project is **not affiliated with, endorsed by, or sponsored by** First Bus / FirstGroup, Google, Google Wallet, or any payment provider.
- “First Bus” is a trademark of its respective owner and is used here only to describe the usage scenario and compatibility.

### Intended use
This module is intended **only** to reduce accidental NFC/contactless payment prompts when displaying a QR ticket screen, which may help prevent unintended or duplicate charges.

### Out of scope
This project is **not** intended to:
- bypass fares or ticketing,
- interfere with POS terminals or payment networks,
- enable fraud or any unauthorized activity.

Use at your own risk. You are responsible for complying with applicable laws and any relevant terms of service.

---

## License

This project is released under the license included in this repository (see `LICENSE`).


---

## FAQ

**Q: Will this prevent all accidental payments?**  
A: No. It aims to reduce the probability. OEM NFC stacks and wallet behavior vary; some devices may still launch wallets in certain conditions.

**Q: Does this modify payment data or interact with the payment terminal?**  
A: No. The module only controls local NFC state/behavior on the phone when the ticket screen is active.

**Q: Why two modes?**  
A: Root mode can reliably control system NFC state. Non-root mode is more constrained and may be less effective on some devices.
