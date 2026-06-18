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
        val channelValues: Map<String, Double?> = emptyMap(),
        val error: String? = null
    )

    suspend fun processDirectory(
        directory: File,
        operation: SignalOperation,
        precision: Precision,
        invertValues: Boolean,
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
            .filter { !it.name.startsWith("output_") && !it.name.startsWith("frequency_summary_") }
            .toList()

        val totalFiles = csvFiles.size
        if (totalFiles == 0) {
            onLog("No CSV files found in the selected directory.")
            return@withContext null
        }

        onLog("Found $totalFiles CSV files. Starting processing...")
        var processedCount = 0

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

        val summaryResults = if (invertValues) {
            results.map { result ->
                result.copy(
                    channelValues = result.channelValues.mapValues { (_, value) ->
                        value?.let { -it }
                    }
                )
            }
        } else {
            results
        }

        val channelNames = summaryResults
            .flatMap { it.channelValues.keys }
            .distinct()
            .sorted()

        if (channelNames.isEmpty()) {
            onLog("No channel data found in the selected CSV files.")
            return@withContext null
        }

        onLog("Detected channels: ${channelNames.joinToString(", ")}")

        onLog("Finished processing. Generating output CSVs...")
        channelNames.forEach { channelName ->
            val safeName = CsvSignalParser.sanitizeChannelFileName(channelName)
            val outputFile = File(targetDir, "output_summary_$safeName.csv")
            outputFile.bufferedWriter().use { writer ->
                writer.write("FileName,Value\n")
                summaryResults.forEach { result ->
                    when {
                        result.error != null -> onLog("Failed to process ${result.filename}: ${result.error}")
                        else -> {
                            val value = result.channelValues[channelName]
                            if (value != null) {
                                writer.write("${result.filename},$value\n")
                            }
                        }
                    }
                }
            }
            onLog("Output saved to: ${outputFile.absolutePath}")

            onLog("Counting value frequencies for $channelName...")
            val valueCounts = mutableMapOf<Double, Int>()
            summaryResults.forEach { result ->
                val value = result.channelValues[channelName]
                if (value != null) {
                    val roundedValue = when (precision) {
                        is Precision.Exact -> value
                        is Precision.Decimals -> {
                            java.math.BigDecimal(value.toString())
                                .setScale(precision.places, java.math.RoundingMode.HALF_UP)
                                .toDouble()
                        }
                    }
                    valueCounts[roundedValue] = valueCounts.getOrDefault(roundedValue, 0) + 1
                }
            }

            val sortedCounts = valueCounts.entries.sortedBy { it.key }
            val freqFile = File(targetDir, "frequency_summary_$safeName.csv")
            freqFile.bufferedWriter().use { writer ->
                writer.write("Value,Count\n")
                sortedCounts.forEach { entry ->
                    writer.write("${entry.key},${entry.value}\n")
                }
            }
            onLog("Frequency counts saved to: ${freqFile.absolutePath}")
        }

        return@withContext targetDir
    }

    internal fun processSingleFile(file: File, operation: SignalOperation): ProcessingResult {
        return try {
            val channelValues = CsvSignalParser.processFile(file, operation)
            ProcessingResult(file.name, channelValues)
        } catch (e: Exception) {
            ProcessingResult(file.name, error = e.message ?: "Unknown error")
        }
    }
}