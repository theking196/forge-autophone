package com.forge.os.api;
import com.forge.os.api.IForgeOsCallback;

interface IForgeOsService {
    int     getApiVersion();
    boolean isReady();
    oneway void askAgent(String threadId, String message, in IForgeOsCallback cb);
    String  listTools();
    String  invokeTool(String toolName, String jsonArgs);
    oneway void invokeToolAsync(String toolName, String jsonArgs, in IForgeOsCallback cb);
    String  getMemory(String key);
    boolean putMemory(String key, String value);
    String  runSkill(String skillId, String jsonArgs);
}
