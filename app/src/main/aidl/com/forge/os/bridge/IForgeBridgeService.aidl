package com.forge.os.bridge;

import com.forge.os.bridge.IForgeBridgeCallback;

/**
 * Forge Bridge — universal tool-provider interface (copy in AutoPhone).
 * Keep in sync with the canonical definition in Forge OS.
 */
interface IForgeBridgeService {
    String getBridgeInfo();
    String getToolManifest();
    String dispatch(String toolName, String argsJson);
    void setCallback(IForgeBridgeCallback callback);
    boolean isReady();
}
