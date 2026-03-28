package org.ayv4zyan.pico_signal_processor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(viewModel: MainViewModel = remember { MainViewModel() }) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    MaterialTheme {
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
            modifier = Modifier.padding(24.dp).fillMaxSize(),
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
                            Text("Browse...")
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
                            Text("Browse...")
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
                    .weight(1f)
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

    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize(),
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
                
                val isExact = precision is Precision.Exact
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isExact,
                        onCheckedChange = { 
                            if (it) {
                                viewModel.setPrecision(Precision.Exact)
                            } else {
                                viewModel.setPrecision(Precision.Decimals(2))
                            }
                        }
                    )
                    Text("Exact Match (No rounding)")
                }

                if (!isExact) {
                    val currentPlaces = (precision as? Precision.Decimals)?.places ?: 2
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
                        label = { Text("Decimal Places") },
                        singleLine = true,
                        modifier = Modifier.width(200.dp)
                    )
                }
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
        }
    }
}