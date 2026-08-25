@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.parkease.feature.ownerparking.data.PhotoUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAX_PHOTO_DIMENSION_PX = 1600
private const val JPEG_QUALITY = 82

@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // A raw phone-camera photo is commonly 5-15+ MB — uploaded as-is, that
    // was slow/large enough to blow past even a widened write timeout on
    // an ordinary mobile connection. Downscaling + re-compressing to a real
    // display-sized JPEG before upload fixes this at the source: smaller,
    // faster, and more reliable regardless of connection speed. Runs on
    // Dispatchers.IO — BitmapFactory decode/compress is real CPU+IO work
    // that shouldn't block the coroutine backing this Composable's
    // rememberCoroutineScope (which defaults to Main). Shared by both the
    // gallery pick and the camera capture below, since a capture ends up
    // as just another content:// Uri once the system Camera app returns.
    fun handlePickedPhoto(uri: Uri?) {
        if (uri == null) return
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) { compressPhotoForUpload(context, uri) }
            if (bytes != null) {
                viewModel.upload(photoType = "LISTING", contentType = "image/jpeg", bytes = bytes)
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        handlePickedPhoto(uri)
    }

    // TakePicture needs a pre-created writable Uri handed in, and reports
    // back only success/failure against that same Uri (not the Uri itself)
    // — pendingCameraUri is how the callback finds out which file it wrote.
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success) handlePickedPhoto(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSourcePicker = true }) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Add photo")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.photos.isEmpty() -> Text(
                    "No photos yet. Add at least one photo of the listing before submitting for review.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.photos, key = { it.id }) { photo ->
                        PhotoTile(photo = photo, onRemove = { viewModel.remove(photo.id) })
                    }
                }
            }

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    if (showSourcePicker) {
        PhotoSourceDialog(
            onTakePhoto = { showSourcePicker = false; launchCamera() },
            onChooseFromGallery = { showSourcePicker = false; pickImageLauncher.launch("image/*") },
            onDismiss = { showSourcePicker = false },
        )
    }
}

@Composable
private fun PhotoSourceDialog(
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a photo") },
        text = {
            Column {
                PhotoSourceOption(Icons.Default.CameraAlt, "Take a photo", onTakePhoto)
                PhotoSourceOption(Icons.Default.PhotoLibrary, "Choose from gallery", onChooseFromGallery)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PhotoSourceOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Creates a fresh, writable content:// Uri (via FileProvider) for the
 * system Camera app to save its capture into — TakePicture() requires the
 * destination Uri up front rather than returning one. Lives under cacheDir
 * since the file is only a transient handoff to compressPhotoForUpload();
 * nothing here is meant to survive past the current upload.
 */
private fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Downsamples and re-encodes the picked image to a JPEG capped at
 * [MAX_PHOTO_DIMENSION_PX] on the longer side. Two-pass decode: first with
 * `inJustDecodeBounds` to read dimensions without allocating pixel memory,
 * then a real decode using the smallest `inSampleSize` that still leaves
 * enough resolution for a final precise scale-down. Returns null if the
 * URI can't be read or decoded (e.g. picker returned an invalid Uri).
 */
private fun compressPhotoForUpload(context: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val (width, height) = bounds.outWidth to bounds.outHeight
    if (width <= 0 || height <= 0) return null

    var sampleSize = 1
    while (width / sampleSize > MAX_PHOTO_DIMENSION_PX * 2 || height / sampleSize > MAX_PHOTO_DIMENSION_PX * 2) {
        sampleSize *= 2
    }

    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null

    val scale = MAX_PHOTO_DIMENSION_PX.toFloat() / maxOf(decoded.width, decoded.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }

    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    if (scaled !== decoded) decoded.recycle()
    scaled.recycle()
    return output.toByteArray()
}

@Composable
private fun PhotoTile(photo: PhotoUi, onRemove: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        AsyncImage(
            model = photo.viewUrl,
            contentDescription = photo.photoType,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
        }
    }
}
