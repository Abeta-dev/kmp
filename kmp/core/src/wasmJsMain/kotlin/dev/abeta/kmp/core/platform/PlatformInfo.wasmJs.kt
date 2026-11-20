package dev.abeta.kmp.core.platform

/** Returns the WebAssembly browser [PlatformInfo]. */
actual fun currentPlatformInfo(): PlatformInfo = PlatformInfo(kind = PlatformKind.WASM_WEB, name = "WasmJS Browser")
