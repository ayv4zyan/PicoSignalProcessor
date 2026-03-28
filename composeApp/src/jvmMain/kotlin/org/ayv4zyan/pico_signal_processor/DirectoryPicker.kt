package org.ayv4zyan.pico_signal_processor

import java.io.File
import javax.swing.JFileChooser

fun openDirectoryPicker(currentDirectory: File? = null): File? {
    // We set up a properties toggle for macOS native dialog if possible,
    // though JFileChooser is generally preferred for DIRECTORIES_ONLY
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    
    val fileChooser = JFileChooser(currentDirectory)
    fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    fileChooser.dialogTitle = "Select Directory containing CSVs"
    
    val result = fileChooser.showOpenDialog(null)
    
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
    
    return if (result == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile
    } else {
        null
    }
}
