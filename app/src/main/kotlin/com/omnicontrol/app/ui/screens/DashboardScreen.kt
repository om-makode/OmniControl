package com.omnicontrol.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.AccentError
import com.omnicontrol.app.ui.theme.AccentPrimary
import com.omnicontrol.app.ui.theme.AccentSecondary
import com.omnicontrol.app.ui.theme.AccentSuccess
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.core.database.dao.SessionDao
import com.omnicontrol.feature.overlay.AssistantService
import com.omnicontrol.feature.permissions.PermissionChecker

@Composable
fun DashboardScreen(
    permissionChecker: PermissionChecker,
    sessionDao: SessionDao,
    onNavigateToPermissions: () -> Unit,
    onNavigateToCapabilities: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDeveloper: () -> Unit
) {
    val context = LocalContext.current
    val permState by permissionChecker.state.collectAsState()
    val sessions by sessionDao.getAllSessions().collectAsState(initial = emptyList())
    val lastSession = sessions.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OmniControl",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Operator Ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Quick permission status chip
            val allReady = permState.allRequiredGranted
            Text(
                text = if (allReady) "System Ready" else "Permissions Needed",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (allReady) AccentSuccess else AccentError,
                modifier = Modifier
                    .background(
                        (if (allReady) AccentSuccess else AccentError).copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Big Start Assistant Button
        Button(
            onClick = {
                if (!permState.allRequiredGranted) {
                    Toast.makeText(context, "Please grant required permissions first", Toast.LENGTH_SHORT).show()
                    onNavigateToPermissions()
                    return@Button
                }
                AssistantService.start(context)
                Toast.makeText(context, "OmniControl Assistant Launched", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (permState.allRequiredGranted) AccentPrimary else DarkSurfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = if (permState.allRequiredGranted) "Launch Floating Assistant" else "Setup Permissions First",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = if (permState.allRequiredGranted) Color.White else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Last session summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AccentSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (lastSession != null) {
                    Text(
                        text = "Last session: ${lastSession.summary ?: lastSession.sessionId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Status: ${lastSession.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    Text(
                        text = "No previous sessions recorded yet. Launch the assistant to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Control Center",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Grid / List of Navigation actions
        DashboardNavButton("Permissions Wizard", "Manage overlay, accessibility & mic", onNavigateToPermissions)
        DashboardNavButton("Device Capabilities", "Review hardware support and status lights", onNavigateToCapabilities)
        DashboardNavButton("Model Manager", "Import & inspect ASR, VAD, LLM models", onNavigateToModels)
        DashboardNavButton("History & Logs", "View session transcripts and actions", onNavigateToHistory)
        DashboardNavButton("Settings", "Configure offline mode, timeouts & behavior", onNavigateToSettings)
        DashboardNavButton("Developer & Spikes", "Run technical spikes and inspect raw logs", onNavigateToDeveloper)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DashboardNavButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Text("→", color = AccentPrimary, fontSize = 20.sp)
        }
    }
}
