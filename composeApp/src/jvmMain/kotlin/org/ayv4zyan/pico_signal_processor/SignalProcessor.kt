package org.ayv4zyan.pico_signal_processor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

enum class SignalOperation {
    MAX, MIN
}

enum class OutputSortBy {
    VALUE, COUNT
}

enum class OutputSortOrder {
    ASCENDING, DESCENDING
}

enum class SummaryFileLayout {
    ONE_FILE, SEPARATE, BOTH
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
        outputSortBy: OutputSortBy,
        outputSortOrder: OutputSortOrder,
        summaryFileLayout: SummaryFileLayout,
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
        val writeConsolidated = summaryFileLayout == SummaryFileLayout.ONE_FILE ||
            summaryFileLayout == SummaryFileLayout.BOTH
        val writeSeparate = summaryFileLayout == SummaryFileLayout.SEPARATE ||
            summaryFileLayout == SummaryFileLayout.BOTH

        if (writeConsolidated) {
            writeConsolidatedOutputSummary(targetDir, channelNames, summaryResults, onLog)
            writeConsolidatedFrequencySummary(
                targetDir = targetDir,
                channelNames = channelNames,
                summaryResults = summaryResults,
                precision = precision,
                outputSortOrder = outputSortOrder,
                onLog = onLog
            )
        }

        if (writeSeparate) {
            channelNames.forEach { channelName ->
                writeSeparateChannelSummaries(
                    targetDir = targetDir,
                    channelName = channelName,
                    summaryResults = summaryResults,
                    precision = precision,
                    outputSortBy = outputSortBy,
                    outputSortOrder = outputSortOrder,
                    onLog = onLog
                )
            }
        }

        return@withContext targetDir
    }

    private fun writeConsolidatedOutputSummary(
        targetDir: File,
        channelNames: List<String>,
        summaryResults: List<ProcessingResult>,
        onLog: (String) -> Unit
    ) {
        val outputFile = File(targetDir, "output_summary.csv")
        outputFile.bufferedWriter().use { writer ->
            writer.write("FileName,${channelNames.joinToString(",")}\n")
            summaryResults.forEach { result ->
                when {
                    result.error != null -> onLog("Failed to process ${result.filename}: ${result.error}")
                    else -> {
                        val values = channelNames.map { channelName ->
                            result.channelValues[channelName]?.toString() ?: ""
                        }
                        writer.write("${result.filename},${values.joinToString(",")}\n")
                    }
                }
            }
        }
        onLog("Output saved to: ${outputFile.absolutePath}")
    }

    private fun writeConsolidatedFrequencySummary(
        targetDir: File,
        channelNames: List<String>,
        summaryResults: List<ProcessingResult>,
        precision: Precision,
        outputSortOrder: OutputSortOrder,
        onLog: (String) -> Unit
    ) {
        onLog("Counting value frequencies for consolidated summary...")
        val countsByChannel = channelNames.associateWith { channelName ->
            buildFrequencyCounts(summaryResults, channelName, precision)
        }
        val allValues = countsByChannel.values
            .flatMap { it.keys }
            .distinct()
        val sortedValues = when (outputSortOrder) {
            OutputSortOrder.ASCENDING -> allValues.sorted()
            OutputSortOrder.DESCENDING -> allValues.sortedDescending()
        }

        val freqFile = File(targetDir, "frequency_summary.csv")
        freqFile.bufferedWriter().use { writer ->
            writer.write("Value,${channelNames.joinToString(",")}\n")
            sortedValues.forEach { value ->
                val counts = channelNames.map { channelName ->
                    countsByChannel[channelName]?.getOrDefault(value, 0) ?: 0
                }
                writer.write("$value,${counts.joinToString(",")}\n")
            }
        }
        onLog("Frequency counts saved to: ${freqFile.absolutePath}")
    }

    private fun writeSeparateChannelSummaries(
        targetDir: File,
        channelName: String,
        summaryResults: List<ProcessingResult>,
        precision: Precision,
        outputSortBy: OutputSortBy,
        outputSortOrder: OutputSortOrder,
        onLog: (String) -> Unit
    ) {
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
        val valueCounts = buildFrequencyCounts(summaryResults, channelName, precision)
        val sortedCounts = sortFrequencyEntries(valueCounts.entries, outputSortBy, outputSortOrder)
        val freqFile = File(targetDir, "frequency_summary_$safeName.csv")
        freqFile.bufferedWriter().use { writer ->
            writer.write("Value,Count\n")
            sortedCounts.forEach { entry ->
                writer.write("${entry.key},${entry.value}\n")
            }
        }
        onLog("Frequency counts saved to: ${freqFile.absolutePath}")
    }

    private fun buildFrequencyCounts(
        summaryResults: List<ProcessingResult>,
        channelName: String,
        precision: Precision
    ): MutableMap<Double, Int> {
        val valueCounts = mutableMapOf<Double, Int>()
        summaryResults.forEach { result ->
            val value = result.channelValues[channelName]
            if (value != null) {
                val roundedValue = roundValue(value, precision)
                valueCounts[roundedValue] = valueCounts.getOrDefault(roundedValue, 0) + 1
            }
        }
        return valueCounts
    }

    private fun roundValue(value: Double, precision: Precision): Double = when (precision) {
        is Precision.Exact -> value
        is Precision.Decimals -> {
            java.math.BigDecimal(value.toString())
                .setScale(precision.places, java.math.RoundingMode.HALF_UP)
                .toDouble()
        }
    }

    internal fun sortFrequencyEntries(
        entries: Set<Map.Entry<Double, Int>>,
        sortBy: OutputSortBy,
        sortOrder: OutputSortOrder
    ): List<Map.Entry<Double, Int>> {
        val comparator = when (sortBy) {
            OutputSortBy.VALUE -> when (sortOrder) {
                OutputSortOrder.ASCENDING -> compareBy<Map.Entry<Double, Int>> { it.key }
                OutputSortOrder.DESCENDING -> compareByDescending<Map.Entry<Double, Int>> { it.key }
            }
            OutputSortBy.COUNT -> when (sortOrder) {
                OutputSortOrder.ASCENDING ->
                    compareBy<Map.Entry<Double, Int>> { it.value }.thenBy { it.key }
                OutputSortOrder.DESCENDING ->
                    compareByDescending<Map.Entry<Double, Int>> { it.value }.thenBy { it.key }
            }
        }
        return entries.sortedWith(comparator)
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