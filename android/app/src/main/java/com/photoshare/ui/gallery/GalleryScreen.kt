package com.photoshare.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.photoshare.data.api.ApiClient
import com.photoshare.data.model.Photo

@Composable
fun GalleryScreen(
    serverUrl: String,
    deviceName: String,
    onUploadClick: () -> Unit,
    vm: GalleryViewModel = viewModel(),
) {
    val photos by vm.photos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val viewedEphemeral by vm.viewedEphemeral.collectAsState()

    var fullscreenPhoto by remember { mutableStateOf<Photo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoShare") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
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

    // Full-screen viewer
    fullscreenPhoto?.let { photo ->
        val alreadyViewed = photo.id in viewedEphemeral
        FullscreenPhotoDialog(
            photo = photo,
            serverUrl = serverUrl,
            alreadyViewed = alreadyViewed,
            deviceName = deviceName,
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
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            // Only uploader can delete
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo.isEphemeral) {
                    Icon(Icons.Default.AutoDelete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(photo.uploaderName ?: "Photo", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("$serverUrl/photos/${photo.id}/file")
                        .crossfade(true)
                        .build(),
                    imageLoader = ApiClient.imageLoader,
                    contentDescription = photo.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        is AsyncImagePainter.State.Error -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { Text("Failed to load image") }
                        else -> SubcomposeAsyncImageContent()
                    }
                }

                photo.caption?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                if (photo.isEphemeral) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This photo disappears after viewing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            }
        },
    )
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
