package com.photoshare

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.photoshare.data.api.ApiClient
import com.photoshare.ui.gallery.GalleryScreen
import com.photoshare.ui.setup.SetupScreen
import com.photoshare.ui.theme.PhotoShareTheme
import com.photoshare.ui.upload.UploadScreen
import com.photoshare.util.AppPrefs
import com.photoshare.util.PreferencesManager
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val shareUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareUri.value = extractShareUri(intent)

        setContent {
            PhotoShareTheme {
                AppRoot(shareUri = shareUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareUri.value = extractShareUri(intent)
    }

    private fun extractShareUri(intent: Intent?): Uri? {
        return when (intent?.action) {
            Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
            else -> null
        }
    }
}

@Composable
private fun AppRoot(shareUri: MutableState<Uri?>) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    // Use collect{} so prefsLoaded=true only fires after the real DataStore read,
    // not on the null initialValue that collectAsStateWithLifecycle starts with.
    var appPrefs by remember { mutableStateOf<AppPrefs?>(null) }
    var prefsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefs.appPrefs.collect { p ->
            if (p != null && !ApiClient.isInitialized()) {
                ApiClient.init(p.serverUrl, p.deviceId, p.deviceName, context)
            }
            appPrefs = p
            prefsLoaded = true
        }
    }

    if (!prefsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Capture once — must not change, or NavHost resets its graph on recomposition
    val initialUri = remember { shareUri.value }
    val startDestination = remember {
        when {
            appPrefs == null -> "setup"
            initialUri != null -> "upload"
            else -> "gallery"
        }
    }
    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navController = rememberNavController()

    // Navigate to upload when a share intent arrives while the app is already running
    LaunchedEffect(shareUri.value) {
        val uri = shareUri.value
        if (uri != null && appPrefs != null) {
            navController.navigate("upload?uri=${Uri.encode(uri.toString())}") {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable("setup") {
            var isSaving by remember { mutableStateOf(false) }
            var setupError by remember { mutableStateOf<String?>(null) }

            SetupScreen(
                isSaving = isSaving,
                error = setupError,
                onSave = { name, serverUrl ->
                    scope.launch {
                        isSaving = true
                        setupError = null
                        val deviceId = UUID.randomUUID().toString()
                        // Verify server is reachable
                        ApiClient.init(serverUrl, deviceId, name, context)
                        val reachable = runCatching {
                            ApiClient.api.health()
                        }.isSuccess
                        if (reachable) {
                            prefs.save(deviceId, name, serverUrl)
                            navController.navigate("gallery") {
                                popUpTo("setup") { inclusive = true }
                            }
                        } else {
                            setupError = "Cannot reach server at $serverUrl"
                        }
                        isSaving = false
                    }
                },
            )
        }

        composable("gallery") {
            val currentPrefs = appPrefs ?: return@composable
            GalleryScreen(
                serverUrl = currentPrefs.serverUrl,
                deviceName = currentPrefs.deviceName,
                deviceId = currentPrefs.deviceId,
                onUploadClick = { navController.navigate("upload") },
                onChangeServer = {
                    scope.launch {
                        prefs.clear()
                        ApiClient.reset()
                        navController.navigate("setup") {
                            popUpTo("gallery") { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(
            route = "upload?uri={uri}",
            arguments = listOf(navArgument("uri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStack ->
            val uriString = backStack.arguments?.getString("uri")
                ?: initialUri?.toString()
            val uri = uriString?.let { Uri.parse(it) }

            LaunchedEffect(Unit) { shareUri.value = null }

            UploadScreen(
                preloadedUri = uri,
                onUploadSuccess = {
                    if (!navController.popBackStack()) {
                        navController.navigate("gallery") {
                            popUpTo("upload") { inclusive = true }
                        }
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("gallery") {
                            popUpTo("upload") { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
