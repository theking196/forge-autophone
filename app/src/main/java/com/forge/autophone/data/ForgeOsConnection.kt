package com.forge.autophone.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.forge.os.api.IForgeOsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "ForgeOsConnection"
private const val FORGE_OS_PKG    = "com.forge.os"
private const val FORGE_OS_ACTION = "com.forge.os.api.IForgeOsService"

/** Forge OS external API permission — must be granted at runtime (dangerous). */
const val FORGE_OS_USE_API_PERMISSION = "com.forge.os.permission.USE_API"

enum class ForgeOsState { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * Manages the AIDL binder lifecycle with Forge OS for status queries.
 * AutoPhone reads [service] to call getApiVersion() on the
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
            Log.i(TAG, "Forge OS connected — API ${runCatching { service?.apiVersion }.getOrNull()}")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _state.value = ForgeOsState.DISCONNECTED
            Log.w(TAG, "Forge OS disconnected")
        }
    }

    /**
     * Binds to Forge OS [IForgeOsService]. Safe to call repeatedly after [UNAVAILABLE]
     * (e.g. user fixed permissions or enabled External API). Skips if [CONNECTED] or [CONNECTING].
     */
    fun bind() {
        when (_state.value) {
            ForgeOsState.CONNECTED, ForgeOsState.CONNECTING -> return
            else -> { }
        }
        if (!isForgeOsInstalled(context)) {
            _state.value = ForgeOsState.UNAVAILABLE
            Log.w(TAG, "Forge OS package not installed: $FORGE_OS_PKG")
            return
        }
        if (ContextCompat.checkSelfPermission(context, FORGE_OS_USE_API_PERMISSION)
            != PackageManager.PERMISSION_GRANTED) {
            _state.value = ForgeOsState.UNAVAILABLE
            Log.w(TAG, "Missing $FORGE_OS_USE_API_PERMISSION — request runtime permission first")
            return
        }
        _state.value = ForgeOsState.CONNECTING
        val intent = Intent(FORGE_OS_ACTION).apply { setPackage(FORGE_OS_PKG) }
        val ok = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse { false }
        if (!ok) {
            _state.value = ForgeOsState.UNAVAILABLE
            Log.w(TAG, "bindService failed — check Forge OS External API and system permission")
        }
    }

    companion object {
        fun isForgeOsInstalled(context: Context): Boolean =
            runCatching {
                context.packageManager.getApplicationInfo(FORGE_OS_PKG, 0)
                true
            }.getOrDefault(false)

        fun hasForgeOsUseApiPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, FORGE_OS_USE_API_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun unbind() {
        if (_state.value == ForgeOsState.DISCONNECTED) return
        runCatching { context.unbindService(connection) }
        service = null
        _state.value = ForgeOsState.DISCONNECTED
    }
}
