package com.forge.os.bridge;

/**
 * Forge Bridge — callback interface (copy in AutoPhone).
 * Keep in sync with the canonical definition in Forge OS.
 */
interface IForgeBridgeCallback {
    oneway void onBridgeEvent(String eventJson);
    oneway void onToolManifestChanged();
    oneway void onBridgeDisconnecting(String reason);
}
