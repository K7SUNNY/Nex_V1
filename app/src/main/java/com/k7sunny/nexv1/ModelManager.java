package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class ModelManager {

    private static final String MODEL_NAME = "qwen2.5-0.5b-instruct.gguf";
    private static final String DOWNLOAD_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";
    private static final String EXPECTED_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";

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

    public boolean isModelFilePresentWithCorrectSize() {
        File file = getModelFile();
        return file != null && file.exists() && file.length() > 300L * 1024L * 1024L;
    }

    public boolean isModelVerified() {
        File file = getModelFile();
        if (!isModelFilePresentWithCorrectSize()) return false;

        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_" + MODEL_NAME, false);
        long verifiedSize = prefs.getLong("verified_size_" + MODEL_NAME, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean verifyModelHash() {
        File file = getModelFile();
        if (!isModelFilePresentWithCorrectSize()) return false;

        String calculated = calculateSHA256(file);
        boolean isValid = EXPECTED_SHA256.equalsIgnoreCase(calculated);
        if (isValid) {
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("verified_" + MODEL_NAME, true)
                    .putLong("verified_size_" + MODEL_NAME, file.length())
                    .apply();
        }
        return isValid;
    }

    private String calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isValidModelFile(File file) {
        return isModelFilePresentWithCorrectSize() && isModelVerified();
    }
}
