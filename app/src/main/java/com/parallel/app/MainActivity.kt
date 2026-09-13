package com.parallel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.parallel.app.ui.ParallelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hub = (application as ParallelApplication).localHubServer
        setContent {
            ParallelTheme {
                var running by remember { mutableStateOf(hub.isRunning) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Text("Parallel", style = MaterialTheme.typography.headlineLarge)
                        Text("Private encrypted local management hub", modifier = Modifier.padding(top = 8.dp))
                        Text(
                            if (running) "Secure browser hub is running on your local network."
                            else "Secure browser hub is stopped.",
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Button(
                            onClick = {
                                if (hub.isRunning) hub.stop() else hub.start()
                                running = hub.isRunning
                            },
                            modifier = Modifier.padding(top = 24.dp)
                        ) { Text(if (running) "Stop secure hub" else "Start secure hub") }
                        if (running) {
                            Text(hub.localUrl(), modifier = Modifier.padding(top = 16.dp))
                            Text("Pairing code", modifier = Modifier.padding(top = 20.dp))
                            Text(hub.pairingToken, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                            Text("Certificate SHA-256", modifier = Modifier.padding(top = 18.dp))
                            Text(hub.certificateFingerprint, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
