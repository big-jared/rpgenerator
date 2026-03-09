package org.bigboyapps.rngenerator.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-specific JPEG/PNG byte array → ImageBitmap decoder.
 */
expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
