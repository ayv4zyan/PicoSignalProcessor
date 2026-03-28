package org.ayv4zyan.pico_signal_processor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

enum class SignalOperation {
    MAX, MIN
}

sealed class Precision {
    object Exact : Precision()
    data class Decimals(val places: Int) : Precision()
}

class SignalProcessor {

    data class ProcessingResult(
        val filename: String,
        val value: Double?,
        val error: String? = null
    )

    suspend fun processDirectory(
        directory: File,
        operation: SignalOperation,
        precision: Precision,
        customOutputDirectory: File?,
        outputFolderSuffix: String,
        onProgress: (Int, Int) -> Unit,
        onLog: (String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            onLog("Error: Invalid input directory selected.")
            return@withContext null
        }

        val baseOutputDir = customOutputDirectory ?: directory
        if (customOutputDirectory != null && (!baseOutputDir.exists() || !baseOutputDir.isDirectory)) {
            onLog("Error: Invalid custom output directory selected.")
            return@withContext null
        }

        val targetDirName = "${directory.name}_$outputFolderSuffix"
        val targetDir = File(baseOutputDir, targetDirName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            onLog("Created output directory: ${targetDir.absolutePath}")
        }

        onLog("Scanning directory for CSV files...")
        val csvFiles = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            .filter { !it.name.startsWith("output_") }
            .toList()

        val totalFiles = csvFiles.size
        if (totalFiles == 0) {
            onLog("No CSV files found in the selected directory.")
            return@withContext null
        }

        onLog("Found $totalFiles CSV files. Starting processing...")
        var processedCount = 0

        // Process files concurrently for better performance
        val results = csvFiles.map { file ->
            async {
                val result = processSingleFile(file, operation)
                val currentCount = synchronized(this@SignalProcessor) {
                    processedCount++
                    processedCount
                }
                withContext(Dispatchers.Main) {
                    onProgress(currentCount, totalFiles)
                }
                result
            }
        }.awaitAll()

        onLog("Finished processing. Generating output CSV...")

        val outputFile = File(targetDir, "output_summary.csv")
        outputFile.bufferedWriter().use { writer ->
            writer.write("FileName,Value\n")
            results.forEach { result ->
                if (result.value != null) {
                    writer.write("${result.filename},${result.value}\n")
                } else if (result.error != null) {
                    onLog("Failed to process ${result.filename}: ${result.error}")
                }
            }
        }

        onLog("Output saved to: ${outputFile.absolutePath}")

        onLog("Counting value frequencies...")
        val valueCounts = mutableMapOf<Double, Int>()
        
        results.forEach { result ->
            val v = result.value
            if (v != null) {
                val roundedValue = when (precision) {
                    is Precision.Exact -> v
                    is Precision.Decimals -> {
                        java.math.BigDecimal(v.toString())
                            .setScale(precision.places, java.math.RoundingMode.HALF_UP)
                            .toDouble()
                    }
                }
                valueCounts[roundedValue] = valueCounts.getOrDefault(roundedValue, 0) + 1
            }
        }
        
        val sortedCounts = valueCounts.entries.sortedBy { it.key }
        
        val freqFile = File(targetDir, "frequency_summary.csv")
        freqFile.bufferedWriter().use { writer ->
            writer.write("Value,Count\n")
            sortedCounts.forEach { entry ->
                // Format decimal to avoid scientific notation if preferred, but standard Double toString is usually fine.
                writer.write("${entry.key},${entry.value}\n")
            }
        }
        
        onLog("Frequency counts saved to: ${freqFile.absolutePath}")

        return@withContext targetDir
    }

    private fun processSingleFile(file: File, operation: SignalOperation): ProcessingResult {
        return try {
            var targetValue: Double? = null

            file.useLines { lines ->
                // Skip header lines (usually 2, sometimes an empty line)
                val dataLines = lines.drop(2)
                for (line in dataLines) {
                    if (line.isBlank()) continue
                    
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val valueStr = parts[1].trim()
                        val value = valueStr.toDoubleOrNull()
                        if (value != null) {
                            if (targetValue == null) {
                                targetValue = value
                            } else {
                                targetValue = when (operation) {
                                    SignalOperation.MAX -> maxOf(targetValue!!, value)
                                    SignalOperation.MIN -> minOf(targetValue!!, value)
                                }
                            }
                        }
                    }
                }
            }
            ProcessingResult(file.name, targetValue)
        } catch (e: Exception) {
            ProcessingResult(file.name, null, e.message ?: "Unknown error")
        }
    }
}
