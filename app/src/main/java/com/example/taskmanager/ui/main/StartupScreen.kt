package com.example.taskmanager.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.ui.components.AppRow
import com.example.taskmanager.ui.components.PermissionPrompt
import com.example.taskmanager.ui.components.StatCard
import com.example.taskmanager.theme.*

@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    val vm: StartupViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.reload() }

    Column(modifier = modifier.fillMaxSize()) {
        StatCard(
            title = "Startup Apps",
            subtitle = "${state.bootApps.size} apps registered for boot",
        ) {
            Text(
                "These apps have registered to run at device startup. To disable a startup app, open its App Info and disable or uninstall it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AccentViolet)
            }
        } else if (state.bootApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No startup apps detected",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(state.bootApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
