package org.ayv4zyan.pico_signal_processor

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeAppDesktopTest {

    @Test
    fun parseSchema_singleChannel() {
        val schema = CsvSignalParser.parseSchema("Time,Channel A").getOrThrow()
        assertEquals(listOf("Channel A"), schema.channelNames)
    }

    @Test
    fun parseSchema_dualChannel() {
        val schema = CsvSignalParser.parseSchema("Time,Channel A,Channel B").getOrThrow()
        assertEquals(listOf("Channel A", "Channel B"), schema.channelNames)
    }

    @Test
    fun parseSchema_threeChannels() {
        val schema = CsvSignalParser.parseSchema("Time,Ch A,Ch B,Ch C").getOrThrow()
        assertEquals(listOf("Ch A", "Ch B", "Ch C"), schema.channelNames)
    }

    @Test
    fun parseSchema_rejectsTimeOnlyHeader() {
        val result = CsvSignalParser.parseSchema("Time")
        assertTrue(result.isFailure)
    }

    @Test
    fun sanitizeChannelFileName_replacesSpacesAndSpecialChars() {
        assertEquals("Channel_A", CsvSignalParser.sanitizeChannelFileName("Channel A"))
        assertEquals("Channel_B", CsvSignalParser.sanitizeChannelFileName("Channel B"))
        assertEquals("Ch_A", CsvSignalParser.sanitizeChannelFileName("Ch A"))
    }

    @Test
    fun processSingleFile_singleChannelMax() {
        val file = createCsv(
            header = "Time,Channel A",
            units = "(ms),(V)",
            rows = listOf(
                "0.0,1.0",
                "1.0,5.0",
                "2.0,3.0"
            )
        )

        val result = SignalProcessor().processSingleFile(file, SignalOperation.MAX)
        assertNull(result.error)
        assertEquals(5.0, result.channelValues["Channel A"])
    }

    @Test
    fun processSingleFile_dualChannelIndependentValues() {
        val file = createCsv(
            header = "Time,Channel A,Channel B",
            units = "(ns),(mV),(mV)",
            rows = listOf(
                "-1.0,1.0,10.0",
                "0.0,5.0,2.0",
                "1.0,3.0,8.0"
            )
        )

        val result = SignalProcessor().processSingleFile(file, SignalOperation.MAX)
        assertNull(result.error)
        assertEquals(5.0, result.channelValues["Channel A"])
        assertEquals(10.0, result.channelValues["Channel B"])
    }

    @Test
    fun processSingleFile_threeChannelMin() {
        val file = createCsv(
            header = "Time,Ch A,Ch B,Ch C",
            units = "(s),(V),(V),(V)",
            rows = listOf(
                "0.0,4.0,7.0,1.0",
                "1.0,2.0,3.0,9.0"
            )
        )

        val result = SignalProcessor().processSingleFile(file, SignalOperation.MIN)
        assertNull(result.error)
        assertEquals(2.0, result.channelValues["Ch A"])
        assertEquals(3.0, result.channelValues["Ch B"])
        assertEquals(1.0, result.channelValues["Ch C"])
    }

    @Test
    fun processSingleFile_invalidHeaderReturnsError() {
        val file = createCsv(
            header = "Time",
            units = "(ms)",
            rows = listOf("0.0")
        )

        val result = SignalProcessor().processSingleFile(file, SignalOperation.MAX)
        assertNotNull(result.error)
        assertTrue(result.channelValues.isEmpty())
    }

    @Test
    fun processDirectory_writesSeparateOutputFilesPerChannel() = runBlocking {
        val inputDir = createTempDirectory("psp-input-").toFile()
        val outputDir = createTempDirectory("psp-output-").toFile()

        createCsvInDir(
            dir = inputDir,
            name = "sample_a.csv",
            header = "Time,Channel A,Channel B",
            units = "(ns),(mV),(mV)",
            rows = listOf(
                "-1.0,1.0,10.0",
                "0.0,5.0,2.0"
            )
        )
        createCsvInDir(
            dir = inputDir,
            name = "sample_b.csv",
            header = "Time,Channel A,Channel B",
            units = "(ns),(mV),(mV)",
            rows = listOf(
                "-1.0,2.0,7.0",
                "0.0,4.0,9.0"
            )
        )

        val processor = SignalProcessor()
        val resultDir = processor.processDirectory(
            directory = inputDir,
            operation = SignalOperation.MAX,
            precision = Precision.Exact,
            invertValues = false,
            customOutputDirectory = outputDir,
            outputFolderSuffix = "PSP_Output",
            onProgress = { _, _ -> },
            onLog = {}
        )

        assertNotNull(resultDir)
        val summaryA = File(resultDir, "output_summary_Channel_A.csv")
        val summaryB = File(resultDir, "output_summary_Channel_B.csv")
        val freqA = File(resultDir, "frequency_summary_Channel_A.csv")
        val freqB = File(resultDir, "frequency_summary_Channel_B.csv")

        assertTrue(summaryA.exists())
        assertTrue(summaryB.exists())
        assertTrue(freqA.exists())
        assertTrue(freqB.exists())

        val summaryALines = summaryA.readLines()
        val summaryBLines = summaryB.readLines()
        assertEquals("FileName,Value", summaryALines.first())
        assertEquals("sample_a.csv,5.0", summaryALines[1])
        assertEquals("sample_b.csv,4.0", summaryALines[2])
        assertEquals("sample_a.csv,10.0", summaryBLines[1])
        assertEquals("sample_b.csv,9.0", summaryBLines[2])
    }

    @Test
    fun processDirectory_invertValues_negatesOutputAndFrequencySummaries() = runBlocking {
        val inputDir = createTempDirectory("psp-input-invert-").toFile()
        val outputDir = createTempDirectory("psp-output-invert-").toFile()

        createCsvInDir(
            dir = inputDir,
            name = "sample_a.csv",
            header = "Time,Channel A,Channel B",
            units = "(ns),(mV),(mV)",
            rows = listOf(
                "-1.0,1.0,10.0",
                "0.0,5.0,2.0"
            )
        )
        createCsvInDir(
            dir = inputDir,
            name = "sample_b.csv",
            header = "Time,Channel A,Channel B",
            units = "(ns),(mV),(mV)",
            rows = listOf(
                "-1.0,2.0,7.0",
                "0.0,4.0,9.0"
            )
        )

        val resultDir = SignalProcessor().processDirectory(
            directory = inputDir,
            operation = SignalOperation.MAX,
            precision = Precision.Exact,
            invertValues = true,
            customOutputDirectory = outputDir,
            outputFolderSuffix = "PSP_Output",
            onProgress = { _, _ -> },
            onLog = {}
        )

        assertNotNull(resultDir)

        val summaryA = File(resultDir, "output_summary_Channel_A.csv").readLines()
        val summaryB = File(resultDir, "output_summary_Channel_B.csv").readLines()
        val freqA = File(resultDir, "frequency_summary_Channel_A.csv").readLines()
        val freqB = File(resultDir, "frequency_summary_Channel_B.csv").readLines()

        assertEquals("sample_a.csv,-5.0", summaryA[1])
        assertEquals("sample_b.csv,-4.0", summaryA[2])
        assertEquals("sample_a.csv,-10.0", summaryB[1])
        assertEquals("sample_b.csv,-9.0", summaryB[2])

        assertEquals("Value,Count", freqA.first())
        assertEquals("-5.0,1", freqA[1])
        assertEquals("-4.0,1", freqA[2])
        assertEquals("-10.0,1", freqB[1])
        assertEquals("-9.0,1", freqB[2])
    }

    @Test
    fun processDirectory_invertValues_disabled_preservesOriginalValues() = runBlocking {
        val inputDir = createTempDirectory("psp-input-no-invert-").toFile()
        val outputDir = createTempDirectory("psp-output-no-invert-").toFile()

        createCsvInDir(
            dir = inputDir,
            name = "sample.csv",
            header = "Time,Channel A",
            units = "(ms),(V)",
            rows = listOf(
                "0.0,-3.0",
                "1.0,2.0"
            )
        )

        val resultDir = SignalProcessor().processDirectory(
            directory = inputDir,
            operation = SignalOperation.MIN,
            precision = Precision.Exact,
            invertValues = false,
            customOutputDirectory = outputDir,
            outputFolderSuffix = "PSP_Output",
            onProgress = { _, _ -> },
            onLog = {}
        )

        assertNotNull(resultDir)

        val summary = File(resultDir, "output_summary_Channel_A.csv").readLines()
        val freq = File(resultDir, "frequency_summary_Channel_A.csv").readLines()

        assertEquals("sample.csv,-3.0", summary[1])
        assertEquals("-3.0,1", freq[1])
    }

    private fun createCsv(
        header: String,
        units: String,
        rows: List<String>
    ): File {
        val file = File.createTempFile("psp-test-", ".csv")
        file.deleteOnExit()
        file.writeText(buildCsvContent(header, units, rows))
        return file
    }

    private fun createCsvInDir(
        dir: File,
        name: String,
        header: String,
        units: String,
        rows: List<String>
    ): File {
        val file = File(dir, name)
        file.writeText(buildCsvContent(header, units, rows))
        return file
    }

    private fun buildCsvContent(
        header: String,
        units: String,
        rows: List<String>
    ): String {
        return buildString {
            appendLine(header)
            appendLine(units)
            appendLine()
            rows.forEach { appendLine(it) }
        }
    }
}