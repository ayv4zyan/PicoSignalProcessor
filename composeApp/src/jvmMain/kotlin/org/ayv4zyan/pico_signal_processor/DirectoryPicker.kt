package org.ayv4zyan.pico_signal_processor

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

fun openDirectoryPicker(currentDirectory: File? = null): File? {
    val osName = System.getProperty("os.name").lowercase()

    return if (osName.contains("mac")) {
        // Native macOS File Dialog
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(null as Frame?, "Select Directory", FileDialog.LOAD)
        if (currentDirectory != null) {
            dialog.directory = currentDirectory.absolutePath
        }
        dialog.isVisible = true
        System.setProperty("apple.awt.fileDialogForDirectories", "false")

        if (dialog.directory != null && dialog.file != null) {
            File(dialog.directory, dialog.file)
        } else {
            null
        }
    } else {
        // Windows/Linux pseudo-native Swing
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val fileChooser = JFileChooser(currentDirectory)
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        fileChooser.dialogTitle = "Select Directory containing CSVs"
        
        val result = fileChooser.showOpenDialog(null)
        
        if (result == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile
        } else {
            null
        }
    }
}
