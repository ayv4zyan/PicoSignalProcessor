package org.ayv4zyan.pico_signal_processor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PicoSignalProcessor",
    ) {
        App()
    }
}