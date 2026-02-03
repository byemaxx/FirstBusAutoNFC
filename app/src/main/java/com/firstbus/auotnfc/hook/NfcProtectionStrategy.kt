package com.firstbus.auotnfc.hook

internal enum class NfcProtectionStrategy(val wireValue: String) {
    ROOT("root"),
    READER_MODE("reader");

    companion object {
        fun fromWireValue(value: String?): NfcProtectionStrategy? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}
