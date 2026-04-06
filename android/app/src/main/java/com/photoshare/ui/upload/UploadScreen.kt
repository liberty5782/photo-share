package com.photoshare.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photoshare.data.api.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    preloadedUri: Uri? = null,
    onUploadSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: UploadViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var selectedUri by remember { mutableStateOf(preloadedUri) }
    var caption by remember { mutableStateOf("") }
    var isEphemeral by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { selectedUri = it } }

    // Navigate away on success
    LaunchedEffect(state) {
        if (state is UploadState.Success) {
            vm.resetState()
            onUploadSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Photo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Photo picker / preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = state !is UploadState.Uploading) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(selectedUri)
                            .crossfade(true)
                            .build(),
                        imageLoader = ApiClient.imageLoader,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap to choose a photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            // Caption
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption (optional)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is UploadState.Uploading,
                maxLines = 3,
            )

            // Ephemeral toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoDelete,
                    contentDescription = null,
                    tint = if (isEphemeral) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ephemeral", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Disappears after each person views it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Switch(
                    checked = isEphemeral,
                    onCheckedChange = { isEphemeral = it },
                    enabled = state !is UploadState.Uploading,
                )
            }

            // Error
            if (state is UploadState.Error) {
                Text(
                    (state as UploadState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Send button
            Button(
                onClick = { selectedUri?.let { vm.upload(it, caption, isEphemeral) } },
                enabled = selectedUri != null && state !is UploadState.Uploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state is UploadState.Uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Send")
                }
            }
        }
    }
}
