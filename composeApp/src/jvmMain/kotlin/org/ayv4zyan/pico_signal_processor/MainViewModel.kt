package org.ayv4zyan.pico_signal_processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { HOME, SETTINGS }

enum class ProcessingState { IDLE, PROCESSING, SUCCESS }

class MainViewModel(private val processor: SignalProcessor = SignalProcessor()) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedDirectory = MutableStateFlow<File?>(null)
    val selectedDirectory = _selectedDirectory.asStateFlow()

    private val _customOutputDirectory = MutableStateFlow<File?>(null)
    val customOutputDirectory = _customOutputDirectory.asStateFlow()
    
    private val _outputFolderSuffix = MutableStateFlow("PSP_Output")
    val outputFolderSuffix = _outputFolderSuffix.asStateFlow()

    private val _operation = MutableStateFlow(SignalOperation.MAX)
    val operation = _operation.asStateFlow()

    private val _precision = MutableStateFlow<Precision>(Precision.Exact)
    val precision = _precision.asStateFlow()

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState = _processingState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    
    // holds the final path for opening it
    var lastSuccessDirectory: File? = null

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectDirectory(dir: File) {
        _selectedDirectory.value = dir
        _processingState.value = ProcessingState.IDLE
        log("Selected input directory: ${dir.absolutePath}")
    }

    fun selectOutputDirectory(dir: File) {
        _customOutputDirectory.value = dir
        log("Selected output directory: ${dir.absolutePath}")
    }
    
    fun setOutputSuffix(suffix: String) {
        _outputFolderSuffix.value = suffix
    }

    fun setOperation(op: SignalOperation) {
        _operation.value = op
        log("Operation set to: $op")
    }

    fun setPrecision(p: Precision) {
        _precision.value = p
        val label = when (p) {
            is Precision.Exact -> "Exact"
            is Precision.Decimals -> "${p.places} Decimals"
        }
        log("Precision set to: $label")
    }

    fun startProcessing() {
        val dir = _selectedDirectory.value
        if (dir == null) {
            log("No directory selected.")
            return
        }

        if (_processingState.value == ProcessingState.PROCESSING) return
        
        _processingState.value = ProcessingState.PROCESSING
        _progress.value = 0f
        
        scope.launch {
            val resultDir = processor.processDirectory(
                directory = dir,
                operation = _operation.value,
                precision = _precision.value,
                customOutputDirectory = _customOutputDirectory.value,
                outputFolderSuffix = _outputFolderSuffix.value,
                onProgress = { current, total ->
                    _progress.value = current.toFloat() / total
                },
                onLog = { msg -> log(msg) }
            )
            
            if (resultDir != null) {
                lastSuccessDirectory = resultDir
                _processingState.value = ProcessingState.SUCCESS
            } else {
                _processingState.value = ProcessingState.IDLE
            }
        }
    }

    private fun log(message: String) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(message)
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs
    }
}
