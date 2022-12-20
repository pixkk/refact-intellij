package com.smallcloud.codify.io

import com.smallcloud.codify.struct.SMCPrediction
import com.google.common.collect.EvictingQueue
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.toPromise
import com.smallcloud.codify.notifications.emitError
import com.smallcloud.codify.struct.SMCRequest
import org.apache.http.HttpResponse
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toActionCallback
import java.util.concurrent.CompletableFuture

import kotlinx.coroutines.*
import java.util.concurrent.Future

private object Cache {
    private val buffer = EvictingQueue.create<Pair<Int, SMCPrediction>>(15)

    fun getFromCache(request: SMCRequest): SMCPrediction? {
        synchronized(this) {
            val hash = request.hashCode()
            val elem = buffer.find { it.first == hash }
            return elem?.second
        }
    }

    fun addCache(request: SMCRequest, prediction: SMCPrediction) {
        synchronized(this) {
            val hash = request.hashCode()
            buffer.add(Pair(hash, prediction))
        }
    }

}

private fun fetchRequest(req: SMCRequest): SMCPrediction? {
    val gson = Gson()
    val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer ${req.token}",
    )
    val json = gson.toJson(req.body)
    return try {
        val response = sendRequest(req.uri, "POST", headers, json.toString())
        // TODO make normal statusCode
//        if (response.statusCode != 200) return null
        gson.fromJson(response.body.toString(), SMCPrediction::class.java)
    } catch (e: Exception) {
        emitError(e.toString())
        null
    }
}

fun fetch(request: SMCRequest): SMCPrediction? {
    val cache = Cache.getFromCache(request)
    if (cache != null) return cache
    Logger.getInstance("fetch").info("fetching the request")
    val prediction = fetchRequest(request) ?: return null
    Cache.addCache(request, prediction)
    return prediction
}

fun inference_fetch(request: SMCRequest): RequestJob? {
    val cache = Cache.getFromCache(request)
    if (cache != null)
        return RequestJob(CompletableFuture.supplyAsync{
            return@supplyAsync cache
        }, null)
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer ${request.token}",
    )

    var job = InferenceGlobalContext.connection?.post(uri, body, headers)
    if (job != null) {
        job.future = job.future.thenApplyAsync {
            return@thenApplyAsync gson.fromJson((it as String), SMCPrediction::class.java)
        }
    }

    return job
}
