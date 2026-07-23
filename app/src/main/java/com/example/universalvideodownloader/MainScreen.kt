package com.example.universalvideodownloader

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DownloaderViewModel = viewModel()) {
    val fetchState by viewModel.fetchState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val remoteMessage by FirebaseManager.updateMessage.collectAsState()
    val remoteUrl by FirebaseManager.updateUrl.collectAsState()
    var url by remember { mutableStateOf("") }

    val formatOptions = listOf(
        "Max Quality" to "bestvideo+bestaudio/best",
        "1080p" to "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
        "720p" to "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
        "480p" to "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
        "Audio Only" to "bestaudio/best"
    )

    var expanded by remember { mutableStateOf(false) }
    var selectedFormatIndex by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val activeDownloads = downloads.filter { it.status != DownloadStatus.COMPLETED }
    val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val currentActiveItem = activeDownloads.firstOrNull { it.status == DownloadStatus.DOWNLOADING }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    fun getCopiedText(): String? {
        val clip = clipboardManager?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            if (remoteMessage.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notice",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = remoteMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (remoteUrl.isNotEmpty()) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(remoteUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Update", color = Color.White)
                            }
                        }
                    }
                }
            }

            Text(
                "Black Hole Downloader",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pull videos from anywhere into your storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Minimalist Black Hole Hero Animation with Circular Fetching & Downloading Progress Ring
            BlackHoleCenterpiece(
                isFetching = fetchState is AppState.Fetching,
                downloadProgress = currentActiveItem?.progress,
                isDownloading = currentActiveItem != null,
                onSingleTap = {
                    val copied = getCopiedText()
                    if (!copied.isNullOrBlank()) {
                        url = copied
                        Toast.makeText(context, "Pasted copied link!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                    }
                },
                onDoubleTap = {
                    val copied = getCopiedText()
                    if (!copied.isNullOrBlank()) {
                        url = copied
                        Toast.makeText(context, "✨ Pulled link from clipboard! Starting download...", Toast.LENGTH_SHORT).show()
                        if (activity != null) AdManager.showAdIfCapped(activity)
                        viewModel.startDirectDownload(context, copied)
                    } else {
                        Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // URL Input Field
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("Paste video link") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = {
                            url = ""
                            viewModel.resetFetchState()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.fetchVideoInfo(context, url) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = url.isNotBlank() && fetchState !is AppState.Fetching
                ) {
                    Text("Fetch Video", fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        if (activity != null) AdManager.showAdIfCapped(activity)
                        viewModel.startDirectDownload(context, url)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = url.isNotBlank()
                ) {
                    Text("Quick Download", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Fetch State UI
            when (val s = fetchState) {
                is AppState.Idle -> {}
                is AppState.Fetching -> {}
                is AppState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (activity != null) AdManager.showAdIfCapped(activity)
                                        viewModel.startDirectDownload(context, url)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Direct Download", color = Color.White)
                                }
                                TextButton(onClick = { viewModel.resetFetchState() }) {
                                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                is AppState.InfoReady -> {
                    val info = s.info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            info.thumbnail?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Text(
                                text = info.title ?: "Untitled Video",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            info.uploader?.let { uploader ->
                                Text(
                                    text = uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Quality Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatOptions[selectedFormatIndex].first, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    formatOptions.forEachIndexed { index, pair ->
                                        DropdownMenuItem(
                                            text = { Text(pair.first) },
                                            onClick = {
                                                selectedFormatIndex = index
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (activity != null) AdManager.showAdIfCapped(activity)
                                    viewModel.startDownload(
                                        context = context,
                                        url = url,
                                        title = info.title ?: "Video",
                                        thumbnailUrl = info.thumbnail,
                                        formatQuery = formatOptions[selectedFormatIndex].second
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Download Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Active Downloads (${activeDownloads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    activeDownloads.forEach { item ->
                        key(item.id) {
                            ActiveDownloadCard(
                                item = item,
                                onPause = { viewModel.pauseDownload(context, item.id) },
                                onResume = { viewModel.resumeDownload(context, item.id) },
                                onCancel = { viewModel.cancelDownload(context, item.id) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Completed Downloads Section
            if (completedDownloads.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Completed Downloads (${completedDownloads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    completedDownloads.forEach { item ->
                        key(item.id) {
                            CompletedDownloadCard(
                                item = item,
                                onPlay = { openFile(context, item.filePath) },
                                onDelete = { viewModel.deleteDownload(item.id) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Developed by Hafeed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun BlackHoleCenterpiece(
    isFetching: Boolean,
    downloadProgress: Float?,
    isDownloading: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BlackHoleRotation")
    val durationMillis = if (isFetching || isDownloading) 1000 else 3200
    
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isFetching || isDownloading) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val amusingFetchingMessages = listOf(
        "Sucking video bytes into the singularity...",
        "Bending space-time for maximum resolution...",
        "Extracting high-definition media stream...",
        "Warping cosmic video data..."
    )
    var messageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(isFetching) {
        if (isFetching) {
            while (true) {
                delay(1400)
                messageIndex = (messageIndex + 1) % amusingFetchingMessages.size
            }
        }
    }

    val progressVal = downloadProgress ?: 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(210.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onTap = { onSingleTap() }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = angle, scaleX = pulseScale, scaleY = pulseScale)
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Minimalist Monochromatic Dark Slate & Silver Accretion Ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF263238),
                            Color(0xFF455A64),
                            Color(0xFF78909C),
                            Color(0xFF37474F),
                            Color(0xFF263238)
                        ),
                        center = center
                    ),
                    radius = radius - 16.dp.toPx(),
                    style = Stroke(width = 14.dp.toPx())
                )

                // Outer Dark Atmosphere Ring
                drawCircle(
                    color = Color(0xFF1E272C).copy(alpha = 0.6f),
                    radius = radius - 4.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Real-time Circular Progress Ring around Black Hole Center
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 10.dp.toPx()
                val diameter = 135.dp.toPx()
                val topLeftOffset = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                // Background track
                drawArc(
                    color = Color(0xFF263238).copy(alpha = 0.4f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx)
                )

                if (isFetching) {
                    // Continuous spinning progress arc while fetching video link info
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF80DEEA), Color(0xFF00E5FF), Color(0xFF80DEEA))
                        ),
                        startAngle = angle,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                } else if (isDownloading || progressVal > 0f) {
                    // 1% to 100% progress arc while downloading
                    val sweep = (progressVal / 100f) * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color(0xFF80DEEA), Color(0xFF00E5FF), Color(0xFF80DEEA))
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }

            // Central Event Horizon / Black Hole core
            Surface(
                shape = CircleShape,
                color = Color(0xFF0B0E17),
                border = BorderStroke(2.dp, Color(0xFF546E7A).copy(alpha = 0.8f)),
                modifier = Modifier.size(115.dp),
                shadowElevation = 24.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color(0xFF37474F).copy(alpha = 0.4f), Color(0xFF0B0E17))
                            )
                        )
                    }

                    if (isFetching) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF80DEEA),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "FETCHING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF80DEEA)
                            )
                        }
                    } else if (isDownloading && progressVal > 0f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${progressVal.toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "DOWNLOADING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80DEEA)
                            )
                        }
                    } else {
                        Text(
                            "🕳️",
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isFetching) {
            AnimatedContent(
                targetState = amusingFetchingMessages[messageIndex],
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "MessageAnim"
            ) { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        } else if (isDownloading && progressVal > 0f) {
            Text(
                text = "⚡ Pulling video bytes into singularity (${progressVal.toInt()}%)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF80DEEA),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "⚡ Double-Tap Black Hole to Auto-Download Copied Link",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ActiveDownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    item.thumbnailUrl?.let { thumb ->
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val statusSubtext = when (item.status) {
                            DownloadStatus.QUEUED -> "Queued"
                            DownloadStatus.DOWNLOADING -> "${item.speed} · ETA: ${item.eta}s"
                            DownloadStatus.PAUSED -> "Paused"
                            else -> ""
                        }
                        Text(
                            text = statusSubtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else if (item.status == DownloadStatus.PAUSED) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = item.progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sizeInfoText = if (item.totalSize.isNotEmpty()) {
                    "${item.downloadedSize} / ${item.totalSize}"
                } else {
                    ""
                }
                Text(
                    text = sizeInfoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${item.progress.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Saved to Downloads · ${item.totalSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun openFile(context: android.content.Context, filePath: String?) {
    if (filePath.isNullOrEmpty()) {
        Toast.makeText(context, "File path not found", Toast.LENGTH_SHORT).show()
        return
    }
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Downloaded video file does not exist", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Play Video")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("MainScreen", "Failed to open video file", e)
        Toast.makeText(context, "Could not open video player: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
