package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.struct.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

private fun lookForCommonErrors(json: JsonObject, request: SMCRequest): String? {
    if (json.has("detail")) {
        val gson = Gson()
        val detail = gson.toJson(json.get("detail"))
        UsageStats.addStatistic(false, request.stat, request.uri.toString(), detail)
        return detail
    }
    if (json.has("retcode") && json.get("retcode").asString != "OK") {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("status") && json.get("status").asString == "error") {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("error")) {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("error").asJsonObject.get("message").asString
        )
        return json.get("error").asJsonObject.get("message").asString
    }
    return null
}
@Deprecated("Will be removed in next release")
private fun lookForCommonErrors(json: JsonObject, request: SMCRequestOld): String? {
    if (json.has("detail")) {
        val gson = Gson()
        val detail = gson.toJson(json.get("detail"))
        UsageStats.addStatistic(false, request.stat, request.uri.toString(), detail)
        return detail
    }
    if (json.has("retcode") && json.get("retcode").asString != "OK") {
        UsageStats.addStatistic(
            false, request.stat,
            request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("status") && json.get("status").asString == "error") {
        UsageStats.addStatistic(
            false, request.stat,
            request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("error")) {
        UsageStats.addStatistic(
            false, request.stat,
            request.uri.toString(), json.get("error").asJsonObject.get("message").asString
        )
        return json.get("error").asJsonObject.get("message").asString
    }
    return null
}


fun streamedInferenceFetch(
        request: SMCRequest,
        dataReceiveEnded: (String) -> Unit,
        dataReceived: (data: SMCStreamingPeace) -> Unit = {},
): CompletableFuture<Future<*>>? {
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
            "Authorization" to "Bearer ${request.token}",
    )

    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED || !LSPProcessHolder.lspIsWorking) return null

    val job = InferenceGlobalContext.connection.post(
            uri, body, headers,
            stat = request.stat,
            dataReceiveEnded = dataReceiveEnded,
            dataReceived = {body: String, reqId: String ->
                val rawJson = gson.fromJson(body, JsonObject::class.java)
                if (rawJson.has("metering_balance")) {
                    AccountManager.instance.meteringBalance = rawJson.get("metering_balance").asInt
                }

                val json = gson.fromJson(body, SMCStreamingPeace::class.java)
                InferenceGlobalContext.lastAutoModel = json.model
                json.requestId = reqId
                UsageStats.addStatistic(true, request.stat, request.uri.toString(), "")
                dataReceived(json)
            },
            errorDataReceived = {
                lookForCommonErrors(it, request)?.let { message ->
                    throw SMCExceptions(message)
                }
            },
            requestId=request.id
    )

    return job
}

@Deprecated("Will be removed in next release")
fun streamedInferenceFetchOld(
    request: SMCRequestOld,
    dataReceiveEnded: (String) -> Unit = {},
    dataReceived: (data: SMCPrediction) -> Unit = {},
): CompletableFuture<Future<*>>? {
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
        "Authorization" to "Bearer ${request.token}",
    )

    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED || !LSPProcessHolder.lspIsWorking) return null

    val job = InferenceGlobalContext.connection.post(
        uri, body, headers,
        stat = request.stat,
        dataReceiveEnded = dataReceiveEnded,
        dataReceived = {body: String, reqId: String ->
            val rawJson = gson.fromJson(body, JsonObject::class.java)
            if (rawJson.has("metering_balance")) {
                AccountManager.instance.meteringBalance = rawJson.get("metering_balance").asInt
            }

            val json = gson.fromJson(body, SMCPrediction::class.java)
            InferenceGlobalContext.lastAutoModel = json.model
            UsageStats.addStatistic(true, request.stat, request.uri.toString(), "")
            dataReceived(json)
        },
        errorDataReceived = {
            lookForCommonErrors(it, request)?.let { message ->
                throw SMCExceptions(message)
            }
        }
    )

    return job
}

