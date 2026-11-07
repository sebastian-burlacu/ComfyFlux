package com.eltonkola.comfyflux.app.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.eltonkola.comfyflux.R
import com.eltonkola.comfyflux.app.AppScreens
import com.eltonkola.comfyflux.app.MainViewModel
import com.eltonkola.comfyflux.data.AppSettings
import com.eltonkola.comfyflux.data.LogStore
import com.eltonkola.comfyflux.ui.theme.Ikona
import com.eltonkola.comfyflux.ui.theme.ikona.Back
import com.eltonkola.comfyflux.ui.theme.ikona.Key
import com.eltonkola.comfyflux.ui.theme.ikona.Paste
import org.koin.compose.koinInject
import com.eltonkola.comfyflux.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    navController: NavController
) {


    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.settingsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Settings")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Ikona.Back,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                // The screen outgrew one viewport when the enrichment fields
                // were added; without this the bottom half (and the logs
                // button) was unreachable.
                .verticalScroll(rememberScrollState()),
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "System theme",
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.system,
                    onCheckedChange = {
                        viewModel.updateSystemTheme(it)
                    }
                )
                // Always visible; only meaningful when the app picks its
                // own theme instead of following Android.
                Text(
                    text = "Dark Mode",
                    fontSize = 20.sp,
                    color = if (uiState.system)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.dark,
                    enabled = !uiState.system,
                    onCheckedChange = {
                        viewModel.updateDarkTheme(it)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            //dynamic colors are on android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(
                    text = "Dynamic color",
                    fontSize = 20.sp
                )
                Switch(
                    checked = uiState.dynamicColor,
                    onCheckedChange = {
                        viewModel.updateDynamicColor(it)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }



            // Wired straight to AppSettings (a Koin singleton) rather than
            // MainViewModel; its flow already backs uiState below.
            val appSettings: AppSettings = koinInject()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hide built-in workflows",
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.hideBundledWorkflows,
                    onCheckedChange = {
                        appSettings.setHideBundledWorkflows(it)
                    }
                )
            }
            Text(
                text = "Removes the sample workflows that ship with the app from the workflow list. Workflows saved on your server or on this device are unaffected.",
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Verbose logging",
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.verboseLogging,
                    onCheckedChange = {
                        viewModel.updateVerboseLogging(it)
                    }
                )
            }
            Text(
                text = "Keeps HTTP request traces, websocket events and error stack traces in Settings -> Logs. Turn on when reporting a problem.",
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log request bodies",
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.logBodies,
                    onCheckedChange = {
                        viewModel.updateLogBodies(it)
                    }
                )
            }
            Text(
                text = "Also keeps the full JSON every generation request sends (workflow, prompt, seed, models). Very verbose; only for deep debugging.",
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Prompt enrichment API key",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Used when the enrichment endpoint needs a key (Groq by default). The magic wand in the prompt field expands short prompts; nothing is sent until you press it. Free key: console.groq.com.",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                label = {
                    Text(text = "API KEY")
                },
                supportingText = {
                    Text(text = "Leave empty to keep prompt enrichment off.")
                },
                value = uiState.grqApiKey,
                onValueChange = {
                    viewModel.setGrqApiKey(it)
                },
                readOnly = true,
                maxLines = 2,
                textStyle = TextStyle(fontSize = 10.sp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    HelpIconButton(
                        onClick = {
                            clipboardManager.getText()?.let {
                                viewModel.setGrqApiKey(it.text)
                            }
                        },
                        imageVector = Ikona.Paste,
                        help = "Paste the API key from the clipboard"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Ikona.Key,
                        contentDescription = "Key",
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enrichment endpoint & model",
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Any OpenAI-compatible chat endpoint works: Groq (default when empty), OpenRouter, OpenAI, llama.cpp, or a local Ollama server like http://192.168.1.5:11434/v1 (model e.g. qwen2.5:7b-instruct, no key needed).",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                label = {
                    Text(text = "Endpoint base URL")
                },
                supportingText = {
                    Text(text = "Empty uses https://api.groq.com/openai/v1")
                },
                value = uiState.enhanceEndpoint,
                onValueChange = {
                    viewModel.setEnhanceEndpoint(it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                label = {
                    Text(text = "Model name")
                },
                supportingText = {
                    Text(text = "Empty uses openai/gpt-oss-20b")
                },
                value = uiState.enhanceModel,
                onValueChange = {
                    viewModel.setEnhanceModel(it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val logStore: LogStore = koinInject()
            val logCount by logStore.entries.collectAsState(initial = emptyList())
            TextButton(onClick = { navController.navigate(AppScreens.Logs.screenName) }) {
                Text(text = if (logCount.isEmpty()) "View logs" else "View logs (${logCount.size})")
            }

            // (no weight spacer inside a scrolling column)
            Spacer(modifier = Modifier.height(24.dp))
            val context = LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo), contentDescription = "logo"
                )
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(
                        id = R.string.about_version,
                        context.getVersionNameAndCode()
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = stringResource(id = R.string.about_body),
                    style = MaterialTheme.typography.labelSmall
                )
                val link = stringResource(id = R.string.about_link)
                TextButton(onClick = { context.openLinkInBrowser(link) }) {
                    Text(text = link)
                }

            }

        }

    }

}

fun Context.getVersionNameAndCode(): String {
    val packageInfo: PackageInfo? = try {
        packageManager.getPackageInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    return packageInfo?.let {
        "${it.versionName} (${it.longVersionCode})"
    } ?: ""
}


fun Context.openLinkInBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}
