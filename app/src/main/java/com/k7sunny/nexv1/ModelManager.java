package com.k7sunny.nexv1;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class ModelManager {

    public static final String MODEL_FAST = "fast";
    public static final String MODEL_PRO = "pro";
    public static final String MODEL_ULTRA = "ultra";

    private final Context context;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private String getCurrentModelKey() {
        return new PreferenceManager(context).getSelectedModel();
    }

    private String getModelFileName(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "qwen2.5-1.5b-instruct-q4_k_m.gguf";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "llama-3.2-3b-instruct-q4_k_m.gguf";
        } else {
            return "qwen2.5-0.5b-instruct.gguf";
        }
    }

    private String getModelUrl(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf?download=true";
        } else {
            return "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";
        }
    }

    private String getModelExpectedHash(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff";
        } else {
            return "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
        }
    }

    private long getExpectedMinSize(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return 900L * 1024L * 1024L; // ~900 MB
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return 1700L * 1024L * 1024L; // ~1.7 GB
        } else {
            return 300L * 1024L * 1024L; // ~300 MB
        }
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
        return new File(getModelDirectory(), getModelFileName(getCurrentModelKey()));
    }

    public boolean isModelDownloaded() {
        return isValidModelFile(getModelFile());
    }

    public String getModelPath() {
        return getModelFile().getAbsolutePath();
    }

    public long downloadModel() {
        File modelFile = getModelFile();
        if (modelFile.exists()) {
            try {
                modelFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String modelKey = getCurrentModelKey();
        String url = getModelUrl(modelKey);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Nex AI Model (" + modelKey.toUpperCase() + ")")
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
        String modelKey = getCurrentModelKey();
        return file != null && file.exists() && file.length() > getExpectedMinSize(modelKey);
    }

    public boolean isModelVerified() {
        File file = getModelFile();
        if (!isModelFilePresentWithCorrectSize()) return false;

        String modelName = getModelFileName(getCurrentModelKey());
        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_" + modelName, false);
        long verifiedSize = prefs.getLong("verified_size_" + modelName, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean verifyModelHash() {
        File file = getModelFile();
        if (!isModelFilePresentWithCorrectSize()) return false;

        String modelKey = getCurrentModelKey();
        String expectedHash = getModelExpectedHash(modelKey);
        String calculated = calculateSHA256(file);
        boolean isValid = expectedHash.equalsIgnoreCase(calculated);
        if (isValid) {
            String modelName = getModelFileName(modelKey);
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("verified_" + modelName, true)
                    .putLong("verified_size_" + modelName, file.length())
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
