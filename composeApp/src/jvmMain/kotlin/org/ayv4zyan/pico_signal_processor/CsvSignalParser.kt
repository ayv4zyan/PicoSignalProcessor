package org.ayv4zyan.pico_signal_processor

import java.io.File

data class CsvSchema(val channelNames: List<String>)

object CsvSignalParser {

    fun parseSchema(headerLine: String): Result<CsvSchema> {
        val columns = headerLine.split(",").map { it.trim() }
        if (columns.size < 2) {
            return Result.failure(IllegalArgumentException("Header must contain at least Time and one channel column"))
        }

        val channelNames = columns.drop(1).filter { it.isNotBlank() }
        if (channelNames.isEmpty()) {
            return Result.failure(IllegalArgumentException("No channel columns found in header"))
        }

        return Result.success(CsvSchema(channelNames))
    }

    fun sanitizeChannelFileName(channelName: String): String {
        return channelName
            .trim()
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "Channel" }
    }

    fun processFile(
        file: File,
        operation: SignalOperation
    ): Map<String, Double?> {
        val lines = file.readLines()
        if (lines.isEmpty()) {
            throw IllegalArgumentException("File is empty")
        }

        val schema = parseSchema(lines.first()).getOrElse { throw it }
        val accumulators = schema.channelNames.associateWith { mutableListOf<Double>() }

        lines.drop(2).forEach { line ->
            if (line.isBlank()) return@forEach

            val parts = line.split(",")
            schema.channelNames.forEachIndexed { index, channelName ->
                val columnIndex = index + 1
                if (columnIndex < parts.size) {
                    parts[columnIndex].trim().toDoubleOrNull()?.let { value ->
                        accumulators.getValue(channelName).add(value)
                    }
                }
            }
        }

        return schema.channelNames.associateWith { channelName ->
            val values = accumulators.getValue(channelName)
            if (values.isEmpty()) {
                null
            } else {
                when (operation) {
                    SignalOperation.MAX -> values.max()
                    SignalOperation.MIN -> values.min()
                }
            }
        }
    }
}