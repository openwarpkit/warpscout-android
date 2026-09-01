package io.github.openwarpkit.warpscout.core

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreBridge @Inject constructor() {
    fun start(requestJson: String, onEvent: (String) -> Unit) {
        val api = apiClass()
        val listenerType = Class.forName("mobileapi.Listener")
        val listener = Proxy.newProxyInstance(
            listenerType.classLoader,
            arrayOf(listenerType)
        ) { _, method, arguments ->
            if (method.name == "onEvent" && arguments?.size == 1) {
                onEvent(arguments[0] as String)
            }
            null
        }
        try {
            api.getMethod("start", String::class.java, listenerType).invoke(null, requestJson, listener)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    fun cancel() {
        runCatching { apiClass().getMethod("cancel").invoke(null) }
    }

    fun stop() {
        runCatching { apiClass().getMethod("stop").invoke(null) }
    }

    fun coreVersion(): String = version("coreVersion")

    fun upstreamVersion(): String = version("upstreamVersion")

    fun generateI1(host: String): String = try {
        apiClass().getMethod("generateI1", String::class.java).invoke(null, host) as String
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    fun available(): Boolean = runCatching { apiClass() }.isSuccess

    private fun version(method: String): String = runCatching {
        apiClass().getMethod(method).invoke(null) as String
    }.getOrDefault("unavailable")

    private fun apiClass(): Class<*> = Class.forName("mobileapi.Mobileapi")
}
