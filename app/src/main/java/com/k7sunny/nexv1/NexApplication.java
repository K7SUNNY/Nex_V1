package com.k7sunny.nexv1;

import android.app.Application;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NexApplication extends Application {

    private static final String TAG = "NexApp";
    private static NexApplication instance;
    private AIManager aiManager;
    private ExecutorService dbExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "NexApplication created, initializing singleton AIManager and dbExecutor...");
        aiManager = new AIManager();
        dbExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "nex-app-db-thread"));
    }

    public static NexApplication getInstance() {
        return instance;
    }

    public AIManager getAiManager() {
        return aiManager;
    }

    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        if (aiManager != null) {
            Log.d(TAG, "NexApplication terminating, releasing AIManager...");
            aiManager.release();
        }
    }
}
