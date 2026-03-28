package org.ayv4zyan.pico_signal_processor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import java.awt.Desktop
import androidx.compose.foundation.isSystemInDarkTheme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF332D41),
    onTertiary = Color(0xFF492532),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(viewModel: MainViewModel = remember { MainViewModel() }) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
    val systemInDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pico Signal Processor", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (currentScreen == Screen.HOME) {
                            IconButton(onClick = { viewModel.navigateTo(Screen.SETTINGS) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    navigationIcon = {
                        if (currentScreen == Screen.SETTINGS) {
                            IconButton(onClick = { viewModel.navigateTo(Screen.HOME) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // AnimatedContent for screen transitions
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState == Screen.SETTINGS) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
                        }
                    }
                ) { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(viewModel)
                        Screen.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val selectedDir by viewModel.selectedDirectory.collectAsState()
    val customOutputDir by viewModel.customOutputDirectory.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val logs by viewModel.logs.collectAsState()

    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Directories Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Input
                Column {
                    Text("1. Input Directory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                val picked = openDirectoryPicker(selectedDir)
                                if (picked != null) viewModel.selectDirectory(picked)
                            },
                            enabled = processingState != ProcessingState.PROCESSING
                        ) {
                            Text("Browse")
                        }
                        Text(
                            text = selectedDir?.absolutePath ?: "No input directory selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedDir != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Output
                Column {
                    Text("2. Output Directory (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                val picked = openDirectoryPicker(customOutputDir ?: selectedDir)
                                if (picked != null) viewModel.selectOutputDirectory(picked)
                            },
                            enabled = processingState != ProcessingState.PROCESSING
                        ) {
                            Text("Browse")
                        }
                        Text(
                            text = customOutputDir?.absolutePath ?: "Default: Same as input directory",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (customOutputDir != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Start Button & Progress
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (processingState == ProcessingState.SUCCESS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Finished", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Finished", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.lastSuccessDirectory?.let { dir ->
                                    try {
                                        Desktop.getDesktop().open(dir)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Open Output Folder")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startProcessing() },
                        enabled = selectedDir != null && processingState == ProcessingState.IDLE,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(0.5f).height(50.dp)
                    ) {
                        Text(
                            if (processingState == ProcessingState.PROCESSING) "Processing..." else "Start Processing",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                AnimatedVisibility(visible = processingState == ProcessingState.PROCESSING || progress > 0f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Logs
            Spacer(Modifier.height(8.dp))
            Text("Logs", style = MaterialTheme.typography.titleSmall)
            val listState = rememberLazyListState()
            
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) {
                    listState.scrollToItem(logs.size - 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val operation by viewModel.operation.collectAsState()
    val precision by viewModel.precision.collectAsState()
    val suffix by viewModel.outputFolderSuffix.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Processor Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // Operation
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Extraction Operation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = operation == SignalOperation.MAX,
                        onClick = { viewModel.setOperation(SignalOperation.MAX) },
                        label = { Text("Maximum Signal") }
                    )
                    FilterChip(
                        selected = operation == SignalOperation.MIN,
                        onClick = { viewModel.setOperation(SignalOperation.MIN) },
                        label = { Text("Minimum Signal") }
                    )
                }
                Text("Controls whether to extract the peak high or peak low line value.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Precision
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Output Precision", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = precision is Precision.Exact,
                        onClick = { viewModel.setPrecision(Precision.Exact) },
                        label = { Text("Exact") }
                    )
                    FilterChip(
                        selected = precision is Precision.Decimals,
                        onClick = { viewModel.setPrecision(Precision.Decimals(2)) },
                        label = { Text("Decimals") }
                    )

                    if (precision is Precision.Decimals) {
                        val currentPlaces = precision.places
                        var textValue by remember(currentPlaces) { mutableStateOf(currentPlaces.toString()) }

                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { 
                                textValue = it
                                val intVal = it.toIntOrNull()
                                if (intVal != null && intVal >= 0) {
                                    viewModel.setPrecision(Precision.Decimals(intVal))
                                }
                            },
                            label = { Text("Places") },
                            singleLine = true,
                            modifier = Modifier.width(90.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text("Exact match doesn't round values. Decimals will round to the specified precision.", 
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Suffix
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Output Folder Suffix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { viewModel.setOutputSuffix(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
                Text("The results folder will be named: InputFolderName_Suffix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Theme Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { 
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) 
                            },
                            leadingIcon = {
                                val icon = when(mode) {
                                    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = if (themeMode == mode) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Text("Choose how Pico Signal Processor looks on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Reset Button
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
            }
        }
    }
}