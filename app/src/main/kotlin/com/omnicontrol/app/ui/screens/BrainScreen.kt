package com.omnicontrol.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.Palette
import com.omnicontrol.core.common.settings.BrainProvider
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.feature.agent.brain.BrainFactory
import com.omnicontrol.feature.agent.brain.LocalLlamaBrain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BrainScreen(settings: SettingsState, store: OmniSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var localModel by remember { mutableStateOf(LocalLlamaBrain.findModel(context)) }

    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        testResult = "Copying model… this can take a minute."
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "models/llm").apply { mkdirs() }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                        ?.takeIf { it.endsWith(".gguf", true) } ?: "model.gguf"
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        File(dir, name).outputStream().use { input.copyTo(it, 1 shl 20) }
                    }
                }.isSuccess
            }
            localModel = LocalLlamaBrain.findModel(context)
            testResult = if (ok) "Model imported." else "Import failed."
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        TopBar("AI brain", onBack)
        Text(
            "Quick commands work without AI. A brain lets OmniControl understand any request, read the screen, " +
                "and do long multi-step tasks. All options below are free.",
            color = Palette.TextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        BrainProvider.entries.forEach { p ->
            ProviderRow(p, selected = settings.brainProvider == p) {
                testResult = null
                scope.launch { store.setBrainProvider(p) }
            }
            Spacer(Modifier.height(8.dp))
        }

        val p = settings.brainProvider
        if (p != BrainProvider.NONE) {
            Spacer(Modifier.height(10.dp))
            SectionTitle("CONFIGURE")
            Card(accent = Palette.Violet) {
                when (p) {
                    BrainProvider.LOCAL -> {
                        Text(
                            if (localModel != null) "Model: ${localModel!!.name} (${localModel!!.length() / 1_000_000} MB)"
                            else "No model yet.",
                            color = Palette.Text, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Bullet("Download a small instruct GGUF (Q4_K_M), e.g. Qwen2.5-3B-Instruct or Llama-3.2-3B-Instruct.")
                        Bullet("Import it below, or push it: adb push model.gguf /data/local/tmp/ then run-as copy (see README).")
                        Bullet("On-device models are slow (5-20 s per step) and can't see images.")
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Import .gguf file") { importModel.launch(arrayOf("*/*")) }
                    }
                    else -> {
                        HelpFor(p)
                        Spacer(Modifier.height(10.dp))
                        Field("Server URL", settings.brainUrl, p.defaultUrl) { v -> scope.launch { store.setBrainUrl(v) } }
                        Field("Model", settings.brainModel, p.defaultModel) { v -> scope.launch { store.setBrainModel(v) } }
                        if (p.needsKey || p == BrainProvider.CUSTOM) {
                            Field("API key", settings.brainApiKey, "paste your key", secret = true) { v -> scope.launch { store.setBrainApiKey(v) } }
                        }
                        ToggleRow(
                            "Send screenshots",
                            if (p.leavesPhone) "Off by default for privacy. Needed for \"the video with a car\". Never sent for personal, banking or password screens."
                            else "Needed for \"the video with a car\" or \"the red button\". The model must support images.",
                            settings.brainVision
                        ) { v -> scope.launch { store.setBrainVision(v) } }
                        if (p.leavesPhone) {
                            Text(
                                "Privacy: each step sends a cleaned-up description of the screen${if (settings.brainVision) " and a screenshot" else ""} to ${p.title.substringBefore(" (")}. " +
                                    "Emails, phone numbers, card numbers and codes are hidden first. Messages, email, contacts, photos, files and banking apps are not sent at all" +
                                    if (settings.allowPersonalCloud) " (except personal apps: you allowed them in Settings)." else ".",
                                color = Palette.Amber, fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (testing) "Testing…" else "Test connection", enabled = !testing) {
                        testing = true
                        testResult = null
                        scope.launch {
                            val brain = BrainFactory.create(context, store.current())
                            testResult = if (brain == null) "✗ ${BrainFactory.whyMissing(context, store.current())}" else {
                                val started = System.currentTimeMillis()
                                brain.ping().fold(
                                    { "✓ Connected to ${brain.name} (${System.currentTimeMillis() - started} ms)" },
                                    { "✗ ${it.message}" }
                                ).also { brain.close() }
                            }
                            testing = false
                        }
                    }
                }
                testResult?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = if (it.startsWith("✓")) Palette.Green else if (it.startsWith("✗")) Palette.Red else Palette.TextDim, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ProviderRow(p: BrainProvider, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.Violet.copy(alpha = 0.12f) else Palette.Card, shape)
            .border(1.dp, if (selected) Palette.Violet else Palette.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) Palette.Violet else Palette.TextDim, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(Palette.Violet, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(p.subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HelpFor(p: BrainProvider) {
    when (p) {
        BrainProvider.MY_COMPUTER -> {
            Bullet("On your Mac: install Ollama (ollama.com), then run: ollama pull qwen2.5vl:7b")
            Bullet("Start it for your Wi-Fi: OLLAMA_HOST=0.0.0.0 ollama serve")
            Bullet("Put your Mac's IP below (System Settings > Wi-Fi > Details), port 11434, ending in /v1.")
            Bullet("LM Studio also works: use its server URL (port 1234) + /v1.")
        }
        BrainProvider.GEMINI -> {
            Bullet("Go to aistudio.google.com → Get API key → Create key (free, no card).")
            Bullet("Paste it below. Free tier has per-minute limits.")
        }
        BrainProvider.GROQ -> {
            Bullet("Go to console.groq.com → API Keys → Create (free).")
            Bullet("Use a vision model (default) to let it see screenshots.")
        }
        BrainProvider.OPENROUTER -> {
            Bullet("Go to openrouter.ai → Keys → Create key.")
            Bullet("Pick any model ending in :free on openrouter.ai/models.")
        }
        else -> Bullet("Base URL must end before /chat/completions (e.g. https://host/v1).")
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, secret: Boolean = false, onChange: (String) -> Unit) {
    var text by remember(label) { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().background(Palette.CardHigh, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (text.isEmpty()) Text(placeholder, color = Palette.TextDim.copy(alpha = 0.6f), fontSize = 14.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it; onChange(it) },
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.Cyan),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
