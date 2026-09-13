package com.parallel.app.hub

import android.content.Context
import com.parallel.app.hub.security.LocalTlsCertificate
import com.parallel.app.hub.security.PairingManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalHubServer(private val context: Context) {
    private var engine: ApplicationEngine? = null
    private var tls: LocalTlsCertificate.Material? = null
    private val pairing = PairingManager()
    private val port = 8765

    val isRunning: Boolean get() = engine != null
    val pairingToken: String get() = pairing.currentToken()
    val certificateFingerprint: String get() = tls?.fingerprintSha256.orEmpty()

    fun start() {
        if (engine != null) return
        val address = localIpv4()
        tls = LocalTlsCertificate(context).loadOrCreate(address)
        val material = tls!!
        pairing.rotate()
        engine = embeddedServer(Netty, configure = {
            sslConnector(
                keyStore = material.keyStore,
                keyAlias = LocalTlsCertificate.KEY_ALIAS,
                keyStorePassword = { LocalTlsCertificate.KEYSTORE_PASSWORD.toCharArray() },
                privateKeyPassword = { material.keyPassword.toCharArray() }
            ) {
                host = "0.0.0.0"
                port = this@LocalHubServer.port
                enabledProtocols = listOf("TLSv1.3", "TLSv1.2")
            }
        }) { module() }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
        tls = null
    }

    fun localUrl(): String = "https://${localIpv4() ?: "127.0.0.1"}:$port/"

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull()
    }.getOrNull()

    private fun Application.module() {
        install(WebSockets)
        routing {
            get("/") { call.respondText(WEB_APP, contentType = ContentType.Text.Html) }
            get("/api/v1/health") {
                call.respondText("{\"status\":\"ok\",\"transport\":\"https\",\"version\":\"0.2.0\"}", contentType = ContentType.Application.Json)
            }
            get("/api/v1/security") {
                if (!paired()) return@get
                call.respondText(
                    "{\"transport\":\"HTTPS\",\"tls\":\"TLSv1.3/TLSv1.2\",\"certificateSha256\":\"$certificateFingerprint\",\"paired\":true}",
                    contentType = ContentType.Application.Json
                )
            }
            webSocket("/ws") {
                var authenticated = false
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    if (!authenticated) {
                        if (text == "PAIR:$pairingToken") {
                            authenticated = true
                            send(Frame.Text("PAIRED"))
                        } else {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Pairing required"))
                            return@webSocket
                        }
                    } else {
                        send(Frame.Text("ECHO:$text"))
                    }
                }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.paired(): Boolean {
        val candidate = request.headers["X-Parallel-Pairing"]
        if (!pairing.verify(candidate.orEmpty())) {
            respondText("Pairing required", status = HttpStatusCode.Unauthorized)
            return false
        }
        return true
    }

    companion object {
        private const val WEB_APP = """
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Parallel Secure Hub</title><style>body{margin:0;background:#080d18;color:#edf2ff;font:15px system-ui,sans-serif}main{max-width:760px;margin:auto;padding:48px 22px}.panel{background:#11192a;border:1px solid #25324d;border-radius:20px;padding:28px}h1{margin:0 0 6px;font-size:38px}p{color:#aab6cf}.row{display:flex;gap:10px;margin-top:22px}input{flex:1;background:#0b1220;color:#fff;border:1px solid #31405f;border-radius:12px;padding:13px}button{background:#eef2ff;color:#101827;border:0;border-radius:12px;padding:13px 18px;font-weight:700}.ok{color:#62e6a5}.error{color:#ff8c8c}.mono{font-family:ui-monospace,monospace;font-size:12px;word-break:break-all;color:#9fb0d0}.hidden{display:none}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-top:22px}.card{padding:18px;border:1px solid #25324d;border-radius:15px;background:#0e1626}</style></head><body><main><div class="panel"><h1>Parallel</h1><p>Private local management hub · encrypted transport</p><div id="pair"><p>Enter the pairing code shown on your phone.</p><div class="row"><input id="token" autocomplete="off" placeholder="Pairing code"><button onclick="pair()">Pair</button></div><p id="error" class="error"></p></div><div id="secure" class="hidden"><p class="ok">● Secure local session established</p><p class="mono" id="fingerprint"></p><div class="grid"><div class="card"><b>Files</b><p>Browse, upload and download</p></div><div class="card"><b>Media</b><p>Photos, video and audio</p></div><div class="card"><b>Messages</b><p>SMS and communications</p></div><div class="card"><b>Device</b><p>Apps, notifications and device info</p></div><div class="card"><b>Screen</b><p>Mirroring and remote actions</p></div><div class="card"><b>Tools</b><p>Notes, RSS, player, cast, chat and timers</p></div></div></div></div></main><script>async function pair(){const t=document.getElementById('token').value.trim();const e=document.getElementById('error');e.textContent='';try{const r=await fetch('/api/v1/security',{headers:{'X-Parallel-Pairing':t}});if(!r.ok)throw Error('Invalid pairing code');const d=await r.json();document.getElementById('fingerprint').textContent='Certificate SHA-256: '+d.certificateSha256;document.getElementById('pair').classList.add('hidden');document.getElementById('secure').classList.remove('hidden')}catch(x){e.textContent=x.message}}</script></body></html>
"""
    }
}
