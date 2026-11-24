package com.omnicontrol.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicontrol.app.ui.theme.AccentError
import com.omnicontrol.app.ui.theme.AccentPrimary
import com.omnicontrol.app.ui.theme.AccentSuccess
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ModelSlotInfo(
    val slot: String,
    val title: String,
    val folder: String,
    val expectedFiles: List<String>,
    var isInstalled: Boolean = false,
    var totalSizeMb: Double = 0.0,
    var fileNames: List<String> = emptyList()
)

@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSlotForImport by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val slots = remember(refreshTrigger) {
        listOf(
            ModelSlotInfo(
                slot = "asr",
                title = "Streaming Speech ASR (Zipformer)",
                folder = "asr",
                expectedFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")
            ),
            ModelSlotInfo(
                slot = "vad",
                title = "Voice Activity Detection (Silero VAD)",
                folder = "vad",
                expectedFiles = listOf("silero_vad.onnx")
            ),
            ModelSlotInfo(
                slot = "kws",
                title = "Keyword Spotter (Stop / Cancel)",
                folder = "kws",
                expectedFiles = listOf("tokens.txt")
            ),
            ModelSlotInfo(
                slot = "llm",
                title = "Local Reasoning Model (GGUF)",
                folder = "llm",
                expectedFiles = listOf("*.gguf")
            )
        )
    }

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val baseDir = File(context.filesDir, "models")
            for (slot in slots) {
                val dir = File(baseDir, slot.folder)
                if (dir.exists()) {
                    val files = dir.listFiles() ?: emptyArray()
                    slot.fileNames = files.map { it.name }
                    slot.totalSizeMb = files.sumOf { it.length() }.toDouble() / (1024 * 1024)
                    slot.isInstalled = if (slot.slot == "llm") {
                        files.any { it.extension == "gguf" }
                    } else {
                        slot.expectedFiles.all { expected -> files.any { it.name == expected } }
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val slot = selectedSlotForImport ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch(Dispatchers.IO) {
            try {
                val targetDir = File(context.filesDir, "models/$slot")
                targetDir.mkdirs()

                val fileName = getFileName(context, uri) ?: "imported_model"
                OmniLog.i("OmniModels", "Importing $fileName into $slot...")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (fileName.endsWith(".zip", ignoreCase = true)) {
                        ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val outFile = File(targetDir, File(entry.name).name)
                                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } else {
                        val outFile = File(targetDir, fileName)
                        FileOutputStream(outFile).use { out -> input.copyTo(out) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported $fileName successfully!", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                }
            } catch (e: Exception) {
                OmniLog.e("OmniModels", "Failed to import model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error importing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Model Manager",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "On-device models stored in app-private storage. No internet needed during inference.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        for (slot in slots) {
            ModelCard(
                info = slot,
                onImport = {
                    selectedSlotForImport = slot.folder
                    importLauncher.launch(arrayOf("*/*"))
                },
                onDelete = {
                    scope.launch(Dispatchers.IO) {
                        val dir = File(context.filesDir, "models/${slot.folder}")
                        dir.deleteRecursively()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Deleted ${slot.title}", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ModelCard(
    info: ModelSlotInfo,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (info.isInstalled) "Installed" else "Missing",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (info.isInstalled) AccentSuccess else AccentError,
                    modifier = Modifier
                        .background(
                            (if (info.isInstalled) AccentSuccess else AccentError).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (info.isInstalled) {
                Text(
                    text = "Size: ${"%.1f".format(info.totalSizeMb)} MB (${info.fileNames.joinToString(", ")})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = "Expected: ${info.expectedFiles.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import File / Zip")
                }
                if (info.isInstalled) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentError)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index)
        }
    }
    return name ?: uri.lastPathSegment
}
