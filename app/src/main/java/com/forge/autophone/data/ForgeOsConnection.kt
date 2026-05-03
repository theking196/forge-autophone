package com.forge.autophone.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.forge.os.api.IForgeOsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "ForgeOsConnection"
private const val FORGE_OS_PKG    = "com.forge.os"
private const val FORGE_OS_ACTION = "com.forge.os.api.IForgeOsService"

enum class ForgeOsState { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * Manages the AIDL binder lifecycle with Forge OS for status queries.
 * AutoPhone reads [service] to call getApiVersion() / isReady() on the
 * Status screen. It does NOT use Forge OS as a command dispatcher — that
 * relationship is reversed (Forge OS calls AutoPhone via IAutoPhoneService).
 */
class ForgeOsConnection(private val context: Context) {

    private val _state = MutableStateFlow(ForgeOsState.DISCONNECTED)
    val state: StateFlow<ForgeOsState> = _state

    var service: IForgeOsService? = null
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IForgeOsService.Stub.asInterface(binder)
            _state.value = ForgeOsState.CONNECTED
            Log.i(TAG, "Forge OS connected — API v${runCatching { service?.apiVersion }.getOrNull()}")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _state.value = ForgeOsState.DISCONNECTED
            Log.w(TAG, "Forge OS disconnected")
        }
    }

    fun bind() {
        if (_state.value != ForgeOsState.DISCONNECTED) return
        _state.value = ForgeOsState.CONNECTING
        val intent = Intent(FORGE_OS_ACTION).apply { setPackage(FORGE_OS_PKG) }
        val ok = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { false }
        if (!ok) {
            _state.value = ForgeOsState.UNAVAILABLE
            Log.w(TAG, "bindService failed — is Forge OS installed?")
        }
    }

    fun unbind() {
        if (_state.value == ForgeOsState.DISCONNECTED) return
        runCatching { context.unbindService(connection) }
        service = null
        _state.value = ForgeOsState.DISCONNECTED
    }
}
