package com.wifi.quickconnect

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.wifi.quickconnect.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var connector: WifiConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connector = WifiConnector(this)

        setContent {
            WifiQuickConnectTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { /* handled via state refresh below */ }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }

                WifiQuickConnectScreen(connector = connector)
            }
        }
    }
}

private enum class Status { IDLE, SCANNING, CONNECTING, SUCCESS, WRONG_PASSWORD, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiQuickConnectScreen(connector: WifiConnector) {
    val scope = rememberCoroutineScope()

    var networks by remember { mutableStateOf(connector.scanResults()) }
    var selected by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(Status.IDLE) }
    var message by remember { mutableStateOf("") }

    fun refresh() {
        status = Status.SCANNING
        connector.startScan()
        networks = connector.scanResults()
        status = Status.IDLE
    }

    LaunchedEffect(Unit) { refresh() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(36.dp))
            Header(onRefresh = ::refresh)
            Spacer(Modifier.height(24.dp))

            if (selected == null) {
                Text(
                    "নেটওয়ার্ক বাছুন",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(networks) { net ->
                        NetworkRow(net) {
                            selected = net
                            password = ""
                            status = Status.IDLE
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                ConnectCard(
                    network = selected!!,
                    password = password,
                    onPasswordChange = { password = it },
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    status = status,
                    message = message,
                    onBack = {
                        selected = null
                        status = Status.IDLE
                    },
                    onConnect = {
                        status = Status.CONNECTING
                        message = ""
                        val ssid = selected!!.ssid
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            scope.launch {
                                connector.connectModern(ssid, password).collect { result ->
                                    status = when (result) {
                                        is ConnectResult.Success -> Status.SUCCESS
                                        is ConnectResult.WrongPassword -> Status.WRONG_PASSWORD
                                        is ConnectResult.Failed -> Status.FAILED
                                    }
                                    message = when (result) {
                                        is ConnectResult.Success -> "✅ '${result.ssid}' এ সফলভাবে কানেক্ট হয়েছে।"
                                        is ConnectResult.WrongPassword -> "❌ পাসওয়ার্ড ভুল অথবা কানেক্ট করা যায়নি।"
                                        is ConnectResult.Failed -> "⚠️ ${result.reason}"
                                    }
                                }
                            }
                        } else {
                            val result = connector.connectLegacy(ssid, password)
                            status = when (result) {
                                is ConnectResult.Success -> Status.SUCCESS
                                is ConnectResult.WrongPassword -> Status.WRONG_PASSWORD
                                is ConnectResult.Failed -> Status.FAILED
                            }
                            message = when (result) {
                                is ConnectResult.Success -> "✅ '${result.ssid}' এ সফলভাবে কানেক্ট হয়েছে।"
                                is ConnectResult.WrongPassword -> "❌ পাসওয়ার্ড ভুল অথবা কানেক্ট করা যায়নি।"
                                is ConnectResult.Failed -> "⚠️ ${result.reason}"
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "WiFi",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                "Quick Connect",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Transparent,
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(AccentStart, AccentEnd))
                )
            )
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceGlass)
                .border(1.dp, SurfaceGlassBorder, CircleShape)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextPrimary)
        }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork, onClick: () -> Unit) {
    val signalColor = when {
        network.levelDbm >= -60 -> SignalStrong
        network.levelDbm >= -75 -> SignalMedium
        else -> SignalWeak
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceGlass)
            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(signalColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (network.isSecured) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = signalColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(network.ssid, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                if (network.isSecured) "সুরক্ষিত নেটওয়ার্ক" else "খোলা নেটওয়ার্ক",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectCard(
    network: WifiNetwork,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    status: Status,
    message: String,
    onBack: () -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceGlassBorder, RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(network.ssid, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (network.isSecured) "পাসওয়ার্ড দিয়ে কানেক্ট করুন" else "ওপেন নেটওয়ার্ক",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("পাসওয়ার্ড", color = TextMuted) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentSolid,
                unfocusedBorderColor = SurfaceGlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentSolid
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))

        val connecting = status == Status.CONNECTING
        Button(
            onClick = onConnect,
            enabled = !connecting && (password.length >= 8 || !network.isSecured),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (connecting) SurfaceGlass
                    else Brush.horizontalGradient(listOf(AccentStart, AccentEnd))
                ),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("কানেক্ট করা হচ্ছে...", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            } else {
                Text("কানেক্ট করুন", color = Color(0xFF0B0E1A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        AnimatedVisibility(visible = message.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val (bg, fg) = when (status) {
                Status.SUCCESS -> SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
                Status.WRONG_PASSWORD, Status.FAILED -> ErrorRed.copy(alpha = 0.15f) to ErrorRed
                else -> SurfaceGlass to TextSecondary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, color = fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
