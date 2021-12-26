package com.vinhdn.rsocketandroid

import android.content.res.AssetManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.websocket.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport
import io.rsocket.kotlin.transport.ktor.client.rSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MainActivity : AppCompatActivity() {

    private lateinit var client: HttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MainScope().launch(Dispatchers.IO) {
            createClient()
        }
    }

    private suspend fun createClient() {
        client = HttpClient(OkHttp) {
            install(WebSockets)
            install(RSocketSupport) {
                connector = RSocketConnector {
                    //configure rSocket connector (all values have defaults)
                    connectionConfig {
                        keepAlive = KeepAlive(
                            interval = 30.seconds,
                            maxLifetime = 2.minutes
                        )

                        //mime types
                        payloadMimeType = PayloadMimeType(
                            data = "application/json",
                            metadata = "application/json"
                        )
                    }

                    //optional acceptor for server requests
                    acceptor {
                        RSocketRequestHandler {
                            requestResponse { it } //echo request payload
                        }
                    }
                }
            }
        }

        //connect to some url
        val rSocket: RSocket = client.rSocket("wss://demo.rsocket.io/rsocket")
        rSocket.fireAndForget(Payload(ByteReadPacket("Hello".toByteArray())))
        //request stream
        val stream: Flow<Payload> = rSocket.requestStream(Payload(ByteReadPacket("Hello".toByteArray())))
        val data = listOf("1", "2", "3", "4").map { Payload(ByteReadPacket(it.toByteArray())) }.asFlow()
        val channels: Flow<Payload> = rSocket.requestChannel(Payload(ByteReadPacket("Hello".toByteArray())), data)
        MainScope().launch(Dispatchers.IO) {
            val bytes = assets.open("data.log").readBytes()
            val streams: Flow<Payload> = rSocket.requestStream(Payload(ByteReadPacket(bytes)))
            streams.collect { payload: Payload ->
                println("stream 2-- take ${payload.data.readText()}")
            }
        }
        //take 5 values and print response
        stream.collect { payload: Payload ->
            println("stream take ${payload.data.readText()}")
        }
        channels.collect { payload: Payload ->
            println("channel take ${payload.data.readText()}")
        }
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}