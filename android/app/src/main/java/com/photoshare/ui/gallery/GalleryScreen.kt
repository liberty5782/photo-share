package com.photoshare.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.photoshare.data.api.ApiClient
import com.photoshare.data.model.Photo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    serverUrl: String,
    deviceName: String,
    deviceId: String,
    onUploadClick: () -> Unit,
    onChangeServer: () -> Unit,
    vm: GalleryViewModel = viewModel(),
) {
    val photos by vm.photos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val viewedEphemeral by vm.viewedEphemeral.collectAsState()

    var fullscreenPhoto by remember { mutableStateOf<Photo?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoShare") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change server") },
                            onClick = {
                                showMenu = false
                                showConfirmDialog = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onUploadClick) {
                Icon(Icons.Default.Add, contentDescription = "Upload photo")
            }
        },
    ) { padding ->

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { vm.loadPhotos() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (photos.isEmpty() && !isLoading) {
                    EmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            val alreadyViewed = photo.id in viewedEphemeral
                            PhotoTile(
                                photo = photo,
                                serverUrl = serverUrl,
                                alreadyViewed = alreadyViewed,
                                onClick = {
                                    if (photo.isEphemeral && !alreadyViewed) {
                                        vm.markViewed(photo.id)
                                    }
                                    fullscreenPhoto = photo
                                },
                            )
                        }
                    }
                }
            }

            if (error != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                    },
                ) { Text(error!!) }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Change server?") },
            text = { Text("This will disconnect from the current server and return to setup.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onChangeServer()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Full-screen viewer
    fullscreenPhoto?.let { photo ->
        val alreadyViewed = photo.id in viewedEphemeral
        FullscreenPhotoDialog(
            photo = photo,
            serverUrl = serverUrl,
            alreadyViewed = alreadyViewed,
            deviceName = deviceName,
            canDelete = photo.uploaderId == deviceId,
            onDismiss = { fullscreenPhoto = null },
            onDelete = {
                vm.deletePhoto(photo.id)
                fullscreenPhoto = null
            },
        )
    }
}

@Composable
private fun PhotoTile(
    photo: Photo,
    serverUrl: String,
    alreadyViewed: Boolean,
    onClick: () -> Unit,
) {
    val showBlur = photo.isEphemeral && !alreadyViewed
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showBlur) {
            // Ghost placeholder — don't load the image yet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoDelete,
                        contentDescription = "Ephemeral",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap to view",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    photo.uploaderName?.let {
                        Text(
                            "from $it",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data("$serverUrl/photos/${photo.id}/file")
                    .crossfade(true)
                    .build(),
                imageLoader = ApiClient.imageLoader,
                contentDescription = photo.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.Gray) }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // Ephemeral badge overlay (bottom-left) — visible after the photo has been loaded
            if (photo.isEphemeral) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.AutoDelete,
                        contentDescription = "Ephemeral",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenPhotoDialog(
    photo: Photo,
    serverUrl: String,
    alreadyViewed: Boolean,
    deviceName: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Photo
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data("$serverUrl/photos/${photo.id}/file")
                    .crossfade(true)
                    .build(),
                imageLoader = ApiClient.imageLoader,
                contentDescription = photo.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is AsyncImagePainter.State.Error -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text("Failed to load image", color = Color.White) }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (photo.isEphemeral) {
                        Icon(
                            Icons.Default.AutoDelete,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        photo.uploaderName ?: "Photo",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (canDelete) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Bottom caption
            val caption = photo.caption?.takeIf { it.isNotBlank() }
            val showBottom = caption != null || photo.isEphemeral
            if (showBottom) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    caption?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                    if (photo.isEphemeral) {
                        Text(
                            "This photo disappears after viewing",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete photo?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No photos yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap + to share a photo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
    }
}
