package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import java.io.File;

public class ModelManager {

    private static final String MODEL_NAME = "qwen2.5-0.5b-instruct.gguf";
    private static final String DOWNLOAD_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";

    private final Context context;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // Keep the model in one stable app-specific directory.
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
        // Qwen2.5-0.5B is roughly 400 MB, so files under 300 MB are treated as invalid.
        return file != null && file.exists() && file.length() > 300L * 1024L * 1024L;
    }
}
