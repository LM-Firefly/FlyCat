package dev.flycat.packer

internal object PayloadFormat {
    const val MAGIC = 0x59445831
    const val VERSION = 2
    const val ROOT = "assets/loader"
    const val METADATA = "$ROOT/payload.bin"
}
