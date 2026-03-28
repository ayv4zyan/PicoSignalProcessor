package org.ayv4zyan.pico_signal_processor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Cursor
import java.awt.Desktop
import java.awt.dnd.*
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Recursively attaches [dt] to this component and all of its descendants.
 * Required on macOS because Compose Desktop renders through a Skiko/Metal child
 * layer that sits above the window's own drop target — so we must cover the
 * entire component tree.
 */
private fun java.awt.Component.attachDropTargetRecursively(dt: DropTarget) {
    dropTarget = dt
    if (this is java.awt.Container) {
        components.forEach { it.attachDropTargetRecursively(dt) }
    }
}


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
fun App(
    viewModel: MainViewModel = remember { MainViewModel() },
    window: ComposeWindow? = null
) {
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
                        Screen.HOME -> HomeScreen(viewModel, window)
                        Screen.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel, window: ComposeWindow?) {
    val selectedDir by viewModel.selectedDirectory.collectAsState()
    val customOutputDir by viewModel.customOutputDirectory.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isDragging by viewModel.isDragging.collectAsState()
    val isLogExpanded by viewModel.isLogExpanded.collectAsState()
    val suffix by viewModel.outputFolderSuffix.collectAsState()

    // macOS Compose Desktop renders through a Skiko/Metal child component that sits
    // above contentPane. We must attach the DropTarget to ALL components recursively
    // to guarantee the OS delivers drag events regardless of which layer is on top.
    LaunchedEffect(window) {
        val target = object : DropTarget() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
                viewModel.setDragging(true)
            }
            override fun dragOver(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            }
            override fun dragExit(dte: DropTargetEvent) {
                viewModel.setDragging(false)
            }
            override fun dropActionChanged(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            }
            @Synchronized
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                        val dir = files?.firstOrNull { it.isDirectory }
                        if (dir != null) {
                            viewModel.selectDirectory(dir)
                            dtde.dropComplete(true)
                            viewModel.setDragging(false)
                            return
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                viewModel.setDragging(false)
                dtde.dropComplete(false)
            }
        }

        // Attach to the full component tree — covers macOS Skiko rendering layer.
        window?.attachDropTargetRecursively(target)

        // Handle any components Compose adds after initial layout.
        window?.addContainerListener(object : java.awt.event.ContainerAdapter() {
            override fun componentAdded(e: java.awt.event.ContainerEvent) {
                e.child.attachDropTargetRecursively(target)
            }
        })
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Drop Zone (Input Hub)
        Box(modifier = Modifier.fillMaxWidth()) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable {
                        val picked = openDirectoryPicker(selectedDir)
                        if (picked != null) viewModel.selectDirectory(picked)
                    }
                    .border(
                        BorderStroke(
                            2.dp,
                            if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (selectedDir != null) Icons.Default.FolderZip else Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = selectedDir?.name ?: "Drop folder here or click to browse",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (selectedDir != null) {
                            Text(
                                text = selectedDir?.absolutePath ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }

            // Clear Button (Standard UI pattern for clearable selection)
            if (selectedDir != null && processingState == ProcessingState.IDLE) {
                IconButton(
                    onClick = { viewModel.selectDirectory(null) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 2: Output Path Quick-Link
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Output, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                val outName = customOutputDir?.name ?: "${selectedDir?.name ?: "Input"}_$suffix"
                Text(
                    text = "Saving to: $outName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = {
                    val picked = openDirectoryPicker(customOutputDir ?: selectedDir)
                    if (picked != null) viewModel.selectOutputDirectory(picked)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Change", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Section 3: Action Center (Start / Processing / Finished)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                if (processingState == ProcessingState.SUCCESS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(56.dp).padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Finished", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                viewModel.lastSuccessDirectory?.let { dir ->
                                    try { Desktop.getDesktop().open(dir) } catch (e: Exception) { e.printStackTrace() }
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Folder")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startProcessing() },
                        enabled = selectedDir != null && processingState == ProcessingState.IDLE,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(0.6f).height(56.dp).shadow(
                            elevation = if (selectedDir != null) 4.dp else 0.dp,
                            shape = RoundedCornerShape(50)
                        )
                    ) {
                        if (processingState == ProcessingState.PROCESSING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Processing...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Processing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Glowing Progress Bar
            AnimatedVisibility(visible = processingState == ProcessingState.PROCESSING || progress > 0f) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Section 4: Collapsible Log Tray
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLogExpanded(!isLogExpanded) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            // ExpandLess = chevron pointing up = currently collapsed, click to open
                            // ExpandMore = chevron pointing down = currently expanded, click to close
                            if (isLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isLogExpanded) "Collapse logs" else "Expand logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Processing Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!isLogExpanded && logs.isNotEmpty()) {
                        Text(
                            text = logs.last(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 16.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isLogExpanded) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                        val p = precision as Precision.Decimals
                        val currentPlaces = p.places
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