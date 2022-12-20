package com.smallcloud.codify.io


import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import org.apache.http.HttpClientConnection
import org.apache.http.HttpHost
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface ConnectionChangedNotifier {
    fun statusChanged(newStatus: ConnectionStatus) {}
    fun lastErrorMsgChanged(newMsg: String?) {}

    companion object {
        val TOPIC = Topic.create(
            "Connection Changed Notifier",
            ConnectionChangedNotifier::class.java
        )
    }
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class RequestJob(var future: CompletableFuture<*>, val request: HttpRequestBase?)

class Connection(uri: URI) {
    private val route: HttpRoute = HttpRoute(HttpHost(uri.host))
    private var context: HttpClientContext = HttpClientContext.create()
    private val connManager: BasicHttpClientConnectionManager = BasicHttpClientConnectionManager()
    private val conn: HttpClientConnection = connManager.requestConnection(route, null).get(10, TimeUnit.SECONDS)

    init {
        connManager.connect(conn, route, 10000, context)
        connManager.routeComplete(conn, route, context)
        connManager.releaseConnection(conn, null, 1, TimeUnit.SECONDS);
    }

    private val client: HttpClient = HttpClients.custom().setConnectionManager(connManager).build()

    fun get(
        uri: URI,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null
    ): RequestJob {
        val get = HttpGet(uri)
        return send(get, headers, requestProperties)
    }

    fun post(
        uri: URI,
        body: String? = null,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null
    ): RequestJob {
        val post = HttpPost(uri)
        post.entity = StringEntity(body)
        return send(post, headers, requestProperties)
    }

    private fun send(
        req: HttpRequestBase,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null
    ): RequestJob {
        headers?.forEach {
            req.addHeader(it.key, it.value)
        }

        requestProperties?.forEach {
            req.addHeader(it.key, it.value)
        }

        val future = CompletableFuture.supplyAsync {
            return@supplyAsync client.execute(req, BasicResponseHandler())
        }
        return RequestJob(future, req)
    }

    var status: ConnectionStatus = ConnectionStatus.CONNECTED
        set(newStatus) {
            if (field == newStatus) return
            field = newStatus
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(ConnectionChangedNotifier.TOPIC)
                .statusChanged(field)
        }
    var lastErrorMsg: String? = null
        set(newMsg) {
            if (field == newMsg) return
            field = newMsg
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(ConnectionChangedNotifier.TOPIC)
                .lastErrorMsgChanged(field)
        }

    protected fun finalize() {
        conn.close()
    }
}
