package com.parallel.app.hub

import android.content.Context
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalHubServer(private val context: Context) {
    private var engine: ApplicationEngine? = null
    val isRunning: Boolean get() = engine != null
    private val port = 8765

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) { module() }.start(wait = false)
    }

    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }

    fun localUrl(): String = "http://${localIpv4() ?: "127.0.0.1"}:$port/"

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull()
    }.getOrNull()

    private fun Application.module() {
        install(WebSockets)
        routing {
            get("/") { call.respondText(WEB_APP, contentType = io.ktor.http.ContentType.Text.Html) }
            get("/api/v1/health") {
                val address = localIpv4() ?: "127.0.0.1"
                call.respondText("{\"status\":\"ok\",\"version\":\"0.1.0\",\"address\":\"$address\"}", contentType = io.ktor.http.ContentType.Application.Json)
            }
            webSocket("/ws") {
                send(Frame.Text("{\"type\":\"hello\",\"app\":\"Parallel\"}"))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val payload = frame.readText().replace("\\", "\\\\").replace("\"", "\\\"")
                        send(Frame.Text("{\"type\":\"echo\",\"data\":\"$payload\"}"))
                    }
                }
            }
        }
    }

    companion object {
        private const val WEB_APP = """
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Parallel</title><style>body{margin:0;background:#0b1020;color:#eef2ff;font:15px system-ui,sans-serif}main{max-width:1000px;margin:auto;padding:48px 24px}.top{display:flex;justify-content:space-between;align-items:center}h1{font-size:38px;margin:0}p{color:#aab4d0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px;margin-top:32px}.card{padding:22px;border:1px solid #26304d;border-radius:18px;background:#121a2e}.status{color:#62e6a5}</style></head><body><main><div class="top"><div><h1>Parallel</h1><p>Private local management hub</p></div><div class="status">● LOCAL</div></div><div class="grid"><div class="card"><b>Files</b><p>Browse, upload and download</p></div><div class="card"><b>Photos & Media</b><p>Local media library</p></div><div class="card"><b>Messages</b><p>SMS and communications</p></div><div class="card"><b>Contacts</b><p>Phone contacts</p></div><div class="card"><b>Notifications</b><p>Live device notifications</p></div><div class="card"><b>Apps & Device</b><p>Installed apps and device info</p></div><div class="card"><b>Screen</b><p>Screen mirror and remote actions</p></div><div class="card"><b>Tools</b><p>Notes, RSS, player, cast, chat, Pomodoro, sound meter</p></div></div><script>fetch('/api/v1/health').then(r=>r.json()).then(console.log)</script></main></body></html>
"""
    }
}
