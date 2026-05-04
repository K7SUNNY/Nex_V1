package com.k7sunny.nexv1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIManager {

    private static final String TAG = "AIManager";

    static {
        System.loadLibrary("nexv1");
    }

    private final ExecutorService executorService;
    private final Handler mainHandler;

    public native String stringFromJNI();

    public interface ResponseCallback {
        void onResponse(String response);
    }

    public AIManager() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Verify native bridge
        Log.d(TAG, "Native bridge test: " + stringFromJNI());
    }

    public void generateResponse(String prompt, ResponseCallback callback) {
        executorService.execute(() -> {
            // Simulate AI processing delay
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String response = "This is a simulated AI response for: \"" + prompt + "\"";

            // Return result to main thread
            mainHandler.post(() -> callback.onResponse(response));
        });
    }
}