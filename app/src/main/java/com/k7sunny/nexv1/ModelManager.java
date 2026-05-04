package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import java.io.File;

public class ModelManager {

    private static final String MODEL_NAME = "model.gguf";
    private static final String DOWNLOAD_URL =
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf";

    private final Context context;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // Always use ONE clean location
    private File getModelDirectory() {
        File dir = new File(context.getExternalFilesDir(null), "models");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getModelFile() {
        return new File(getModelDirectory(), MODEL_NAME);
    }

    public boolean isModelDownloaded() {
        return isValidModelFile(getModelFile());
    }

    public String getModelPath() {
        return getModelFile().getAbsolutePath();
    }

    public long downloadModel() {
        File modelFile = getModelFile();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
                .setTitle("Downloading Nex AI Model")
                .setDescription("Preparing your personal AI workspace...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(modelFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        return downloadManager.enqueue(request);
    }

    public boolean checkModelInStorage() {
        return isModelDownloaded();
    }

    public String getValidModelPath() {
        File modelFile = getModelFile();
        if (isValidModelFile(modelFile)) {
            return modelFile.getAbsolutePath();
        }
        return null;
    }

    private boolean isValidModelFile(File file) {
        // TinyLlama ~700MB → anything <500MB = garbage
        return file != null && file.exists() && file.length() > 500L * 1024L * 1024L;
    }
}