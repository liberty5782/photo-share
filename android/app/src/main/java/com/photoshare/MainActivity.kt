package com.photoshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.photoshare.util.PreferencesManager
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingShareUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShareUri = extractShareUri(intent)

        setContent {
            PhotoShareTheme {
                AppRoot(initialShareUri = pendingShareUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle share intents when app is already running
        extractShareUri(intent)?.let { uri ->
            pendingShareUri = uri
        }
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
private fun AppRoot(initialShareUri: Uri?) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    val appPrefs by prefs.appPrefs.collectAsStateWithLifecycle(initialValue = null)
    // null = still loading; non-null AppPrefs = configured; explicit "unset" = show setup
    var prefsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(appPrefs) {
        prefsLoaded = true
        appPrefs?.let { p ->
            if (!ApiClient.isInitialized()) {
                ApiClient.init(p.serverUrl, p.deviceId, p.deviceName, context)
            }
        }
    }

    if (!prefsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (appPrefs != null) "gallery" else "setup",
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
                onUploadClick = { navController.navigate("upload") },
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
                ?: initialShareUri?.toString()
            val uri = uriString?.let { Uri.parse(it) }

            UploadScreen(
                preloadedUri = uri,
                onUploadSuccess = {
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
