package org.ayv4zyan.pico_signal_processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.prefs.Preferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Screen { HOME, SETTINGS }

enum class ProcessingState { IDLE, PROCESSING, SUCCESS }

class MainViewModel(private val processor: SignalProcessor = SignalProcessor()) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val prefs = Preferences.userNodeForPackage(MainViewModel::class.java)
    private val KEY_DIR = "selected_directory"
    private val KEY_OUT_DIR = "custom_output_directory"
    private val KEY_SUFFIX = "output_suffix"
    private val KEY_OP = "operation"
    private val KEY_PRECISION_TYPE = "precision_type" // 0 = Exact, 1 = Decimals
    private val KEY_PRECISION_VAL = "precision_val"
    private val KEY_THEME = "theme_mode"

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode = _themeMode.asStateFlow()

    private val _isLogExpanded = MutableStateFlow(false)
    val isLogExpanded = _isLogExpanded.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging = _isDragging.asStateFlow()

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

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        prefs.get(KEY_DIR, null)?.let { _selectedDirectory.value = File(it) }
        prefs.get(KEY_OUT_DIR, null)?.let { _customOutputDirectory.value = File(it) }
        _outputFolderSuffix.value = prefs.get(KEY_SUFFIX, "PSP_Output")
        
        val opName = prefs.get(KEY_OP, SignalOperation.MAX.name)
        _operation.value = try { SignalOperation.valueOf(opName) } catch (e: Exception) { SignalOperation.MAX }

        val pType = prefs.getInt(KEY_PRECISION_TYPE, 0)
        _precision.value = if (pType == 0) {
            Precision.Exact
        } else {
            val places = prefs.getInt(KEY_PRECISION_VAL, 2)
            Precision.Decimals(places)
        }

        val themeName = prefs.get(KEY_THEME, ThemeMode.SYSTEM.name)
        _themeMode.value = try { ThemeMode.valueOf(themeName) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.put(KEY_THEME, mode.name)
    }

    fun selectDirectory(dir: File) {
        _selectedDirectory.value = dir
        prefs.put(KEY_DIR, dir.absolutePath)
        _processingState.value = ProcessingState.IDLE
        log("Selected input directory: ${dir.absolutePath}")
    }

    fun selectOutputDirectory(dir: File) {
        _customOutputDirectory.value = dir
        prefs.put(KEY_OUT_DIR, dir.absolutePath)
        _processingState.value = ProcessingState.IDLE
        log("Selected output directory: ${dir.absolutePath}")
    }
    
    fun setOutputSuffix(suffix: String) {
        _outputFolderSuffix.value = suffix
        prefs.put(KEY_SUFFIX, suffix)
        _processingState.value = ProcessingState.IDLE
    }

    fun setOperation(op: SignalOperation) {
        _operation.value = op
        prefs.put(KEY_OP, op.name)
        _processingState.value = ProcessingState.IDLE
        log("Operation set to: $op")
    }

    fun setPrecision(p: Precision) {
        _precision.value = p
        when (p) {
            is Precision.Exact -> {
                prefs.putInt(KEY_PRECISION_TYPE, 0)
            }
            is Precision.Decimals -> {
                prefs.putInt(KEY_PRECISION_TYPE, 1)
                prefs.putInt(KEY_PRECISION_VAL, p.places)
            }
        }
        _processingState.value = ProcessingState.IDLE
        
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

    fun resetToDefaults() {
        prefs.clear()
        _selectedDirectory.value = null
        _customOutputDirectory.value = null
        _outputFolderSuffix.value = "PSP_Output"
        _operation.value = SignalOperation.MAX
        _precision.value = Precision.Exact
        _themeMode.value = ThemeMode.SYSTEM
        _processingState.value = ProcessingState.IDLE
        _progress.value = 0f
        _isLogExpanded.value = false
        _isDragging.value = false
        _logs.value = emptyList()
        log("Settings reset to defaults.")
    }

    fun setLogExpanded(expanded: Boolean) {
        _isLogExpanded.value = expanded
    }

    fun setDragging(dragging: Boolean) {
        _isDragging.value = dragging
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
