package com.forge.os.api;
oneway interface IForgeOsCallback {
    void onToken(String token);
    void onError(String message);
    void onComplete();
}
