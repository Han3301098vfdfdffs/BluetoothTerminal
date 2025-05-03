package com.example.bluetoothterminal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

@SuppressLint("ContextCastToActivity")
@Composable
fun BluetoothAppUI(requestPermission: (String) -> Unit) {
    val viewModel: BluetoothViewModel = viewModel()
    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val connectionState by viewModel.connectionState.collectAsState()
    var receivedData by remember { mutableStateOf("") }

    // Estado para los datos de temperatura
    var temp1 by remember { mutableStateOf("--") }
    var temp2 by remember { mutableStateOf("--") }

    // Launcher para permisos BLUETOOTH_CONNECT
    val connectPermissionLauncher = rememberBluetoothPermissionLauncher(viewModel, context)

    // Launcher para permisos BLUETOOTH_SCAN
    val scanPermissionLauncher = scanBluetoothPermissionLauncher(viewModel, context, connectPermissionLauncher)

    // Launcher para activar Bluetooth
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.updateBluetoothState(result.resultCode == Activity.RESULT_OK)
    }

    // Verificar estado inicial
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermission(Manifest.permission.BLUETOOTH_CONNECT)
            requestPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        viewModel.checkBluetoothState(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Botón para activar Bluetooth
        Button(
            onClick = {
                if (!viewModel.bluetoothEnabled.value) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.bluetoothEnabled.value) "Abrir configuración Bluetooth" else "Activar Bluetooth")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.checkPermissionButton(context, activity, scanPermissionLauncher, connectPermissionLauncher)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Seleccionar dispositivo")
        }

        // Mostrar dispositivo seleccionado (si hay uno)
        viewModel.selectedDevice.value?.let { device ->
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dispositivo seleccionado:", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .clickable {
                        viewModel.selectDevice(device)
                    }
            ) {
                Text(
                    text = device,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }?: run {
            if (viewModel.bluetoothEnabled.value) {
                Text(
                    text = "No hay dispositivo seleccionado",
                    color = Color.Red,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // Mostrar dispositivos emparejados
        if (viewModel.pairedDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dispositivos emparejados:", style = MaterialTheme.typography.titleMedium)
            viewModel.pairedDevices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                viewModel.selectDevice(device)
                            }
                    ) {
                        Text(
                            text = device,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // Estado de conexión
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Estado:",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            val (color, statusText) = when (connectionState) {
                is BluetoothViewModel.ConnectionState.Disconnected ->
                    Pair(Color.Gray, "Deshabilitado")
                is BluetoothViewModel.ConnectionState.Connecting ->
                    Pair(Color.Yellow, "Conectando...")
                is BluetoothViewModel.ConnectionState.Connected ->
                    Pair(Color.Green, "Conectado")
                is BluetoothViewModel.ConnectionState.Error ->
                    Pair(Color.Red, "Error de conexión")
                else -> Pair(Color.Gray, "Desconocido")
            }

            // Punto de estado
            Surface(
                color = color,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(16.dp)
            ) {}

            Text(
                text = statusText,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Datos recibidos
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Datos del sensor:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Temperatura 1", fontWeight = FontWeight.Bold)
                    Text(text = "$temp1 °C", fontSize = 24.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Temperatura 2", fontWeight = FontWeight.Bold)
                    Text(text = "$temp2 °C", fontSize = 24.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de conexión
        Button(
            onClick = {
                when (connectionState) {
                    is BluetoothViewModel.ConnectionState.Disconnected,
                    is BluetoothViewModel.ConnectionState.Error -> {
                        viewModel.selectedDeviceAddress.value?.let { macAddress ->
                            viewModel.connectToDeviceByMac(context, macAddress)
                        } ?: run {
                            Toast.makeText(context, "Selecciona un dispositivo primero", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        viewModel.disconnect()
                        temp1 = "--"
                        temp2 = "--"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(
                text = when (connectionState) {
                    is BluetoothViewModel.ConnectionState.Disconnected,
                    is BluetoothViewModel.ConnectionState.Error -> "Conectar"
                    else -> "Desconectar"
                }
            )
        }

        // Leer datos cuando esté conectado
        LaunchedEffect(connectionState) {
            if (connectionState is BluetoothViewModel.ConnectionState.Connected) {
                withContext(Dispatchers.IO) {
                    try {
                        val socket = viewModel.getBluetoothSocket() ?: return@withContext
                        val inputStream: InputStream = socket.inputStream
                        val buffer = ByteArray(1024)
                        var bytes: Int

                        while (true) {
                            bytes = inputStream.read(buffer)
                            val data = String(buffer, 0, bytes)
                            receivedData += data

                            // Procesar los datos para extraer las temperaturas
                            val lines = data.split("\n")
                            for (line in lines) {
                                when {
                                    line.startsWith("tmp1: ") -> {
                                        temp1 = line.substringAfter("tmp1: ").trim()
                                    }
                                    line.startsWith("tmp2: ") -> {
                                        temp2 = line.substringAfter("tmp2: ").trim()
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}