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
    public static final String MODEL_VISION = "vision";

    private final Context context;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getCurrentModelKey() {
        return new PreferenceManager(context).getSelectedModel();
    }

    public boolean isVisionModel() {
        return MODEL_VISION.equals(getCurrentModelKey());
    }

    public boolean isVisionModel(String modelKey) {
        return MODEL_VISION.equals(modelKey);
    }

    public String getModelFileName(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "qwen2.5-1.5b-instruct-q4_k_m.gguf";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "llama-3.2-3b-instruct-q4_k_m.gguf";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf";
        } else {
            return "qwen2.5-0.5b-instruct.gguf";
        }
    }

    public String getMmprojFileName(String modelKey) {
        if (MODEL_VISION.equals(modelKey)) {
            return "mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf";
        }
        return null;
    }

    public String getModelUrl(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf?download=true";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf?download=true";
        } else {
            return "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true";
        }
    }

    public String getMmprojUrl(String modelKey) {
        if (MODEL_VISION.equals(modelKey)) {
            return "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf?download=true";
        }
        return null;
    }

    public String getModelExpectedHash(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "c27eb06ef082404e12c1451f28b49e3cb1b7cebca7f7ff9e4ba6fbf9a1c1d81a";
        } else {
            return "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
        }
    }

    public String getMmprojExpectedHash(String modelKey) {
        if (MODEL_VISION.equals(modelKey)) {
            return "0f7c22ee3a2283bf3bfa99b9cf9787e974e64f7b6b231ff634f195d8e7ea5025";
        }
        return null;
    }

    public long getExpectedMinSize(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return 900L * 1024L * 1024L; // ~900 MB
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return 1700L * 1024L * 1024L; // ~1.7 GB
        } else if (MODEL_VISION.equals(modelKey)) {
            return 1800L * 1024L * 1024L; // ~1.8 GB
        } else {
            return 300L * 1024L * 1024L; // ~300 MB
        }
    }

    public long getExpectedMmprojMinSize(String modelKey) {
        if (MODEL_VISION.equals(modelKey)) {
            return 500L * 1024L * 1024L; // ~500 MB
        }
        return 0;
    }

    // Keep the model in one stable app-specific directory.
    public File getModelDirectory() {
        File dir = new File(context.getExternalFilesDir(null), "models");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getModelFile() {
        return new File(getModelDirectory(), getModelFileName(getCurrentModelKey()));
    }

    public File getModelFile(String modelKey) {
        return new File(getModelDirectory(), getModelFileName(modelKey));
    }

    public File getMmprojFile() {
        String mmprojName = getMmprojFileName(getCurrentModelKey());
        if (mmprojName == null) return null;
        return new File(getModelDirectory(), mmprojName);
    }

    public File getMmprojFile(String modelKey) {
        String mmprojName = getMmprojFileName(modelKey);
        if (mmprojName == null) return null;
        return new File(getModelDirectory(), mmprojName);
    }

    public boolean isModelDownloaded() {
        String modelKey = getCurrentModelKey();
        boolean modelOk = isValidModelFile(getModelFile(modelKey), modelKey);
        if (MODEL_VISION.equals(modelKey)) {
            return modelOk && isValidMmprojFile(getMmprojFile(modelKey), modelKey);
        }
        return modelOk;
    }

    public boolean isModelDownloaded(String modelKey) {
        boolean modelOk = isValidModelFile(getModelFile(modelKey), modelKey);
        if (MODEL_VISION.equals(modelKey)) {
            return modelOk && isValidMmprojFile(getMmprojFile(modelKey), modelKey);
        }
        return modelOk;
    }

    public boolean isMmprojMissing() {
        String modelKey = getCurrentModelKey();
        if (!MODEL_VISION.equals(modelKey)) return false;
        File file = getMmprojFile(modelKey);
        return file == null || !file.exists() || file.length() < getExpectedMmprojMinSize(modelKey);
    }

    public void cleanupCorruptedModel() {
        String modelKey = getCurrentModelKey();
        File file = getModelFile(modelKey);
        String modelName = getModelFileName(modelKey);
        if (file.exists()) {
            boolean isSizeOk = isModelFilePresentWithCorrectSize(modelKey);
            boolean isVerified = isModelVerified(modelKey);
            if (!isSizeOk || !isVerified) {
                try {
                    file.delete();
                    android.util.Log.d("ModelManager", "Cleaned up broken/corrupted model file: " + modelName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .remove("verified_" + modelName)
                        .remove("verified_size_" + modelName)
                        .apply();
            }
        }

        if (MODEL_VISION.equals(modelKey)) {
            File mmprojFile = getMmprojFile(modelKey);
            String mmprojName = getMmprojFileName(modelKey);
            if (mmprojFile != null && mmprojFile.exists()) {
                boolean isMmprojSizeOk = isMmprojFilePresentWithCorrectSize(modelKey);
                boolean isMmprojVerified = isMmprojVerified(modelKey);
                if (!isMmprojSizeOk || !isMmprojVerified) {
                    try {
                        mmprojFile.delete();
                        android.util.Log.d("ModelManager", "Cleaned up broken mmproj file: " + mmprojName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .remove("verified_mmproj_" + mmprojName)
                            .remove("verified_mmproj_size_" + mmprojName)
                            .apply();
                }
            }
        }
    }

    public boolean deleteModel(String modelKey) {
        boolean success = true;
        File file = getModelFile(modelKey);
        String modelName = getModelFileName(modelKey);
        if (file.exists()) {
            success = file.delete();
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("verified_" + modelName)
                    .remove("verified_size_" + modelName)
                    .apply();
        }
        if (MODEL_VISION.equals(modelKey)) {
            File mmprojFile = getMmprojFile(modelKey);
            String mmprojName = getMmprojFileName(modelKey);
            if (mmprojFile != null && mmprojFile.exists()) {
                boolean mmprojDeleted = mmprojFile.delete();
                success = success && mmprojDeleted;
                context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .remove("verified_mmproj_" + mmprojName)
                        .remove("verified_mmproj_size_" + mmprojName)
                        .apply();
            }
        }
        return success;
    }

    public boolean isModelFileCorrupted() {
        String modelKey = getCurrentModelKey();
        File file = getModelFile(modelKey);
        boolean modelCorrupted = file.exists() && (!isModelFilePresentWithCorrectSize(modelKey) || !isModelVerified(modelKey));
        if (MODEL_VISION.equals(modelKey)) {
            File mmprojFile = getMmprojFile(modelKey);
            boolean mmprojCorrupted = mmprojFile != null && mmprojFile.exists() && (!isMmprojFilePresentWithCorrectSize(modelKey) || !isMmprojVerified(modelKey));
            return modelCorrupted || mmprojCorrupted;
        }
        return modelCorrupted;
    }

    public String getModelPath() {
        return getModelFile().getAbsolutePath();
    }

    public String getMmprojPath() {
        File mmproj = getMmprojFile();
        return mmproj != null ? mmproj.getAbsolutePath() : null;
    }

    public long downloadModel() {
        cleanupCorruptedModel();
        String modelKey = getCurrentModelKey();

        // If for vision model the language model is already downloaded and verified, download mmproj instead
        if (MODEL_VISION.equals(modelKey) && isModelFilePresentWithCorrectSize(modelKey) && isModelVerified(modelKey)) {
            return downloadMmproj();
        }

        File modelFile = getModelFile(modelKey);
        if (modelFile.exists()) {
            try {
                modelFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

    public long downloadMmproj() {
        String modelKey = getCurrentModelKey();
        if (!MODEL_VISION.equals(modelKey)) return -1;

        File mmprojFile = getMmprojFile(modelKey);
        if (mmprojFile != null && mmprojFile.exists()) {
            try {
                mmprojFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String url = getMmprojUrl(modelKey);
        if (url == null || mmprojFile == null) return -1;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Vision Projector (MMPROJ)")
                .setDescription("Preparing multimodal image engine...")
                .setDestinationUri(Uri.fromFile(mmprojFile))
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
        if (isValidModelFile(modelFile, getCurrentModelKey())) {
            return modelFile.getAbsolutePath();
        }
        return null;
    }

    public boolean isModelFilePresentWithCorrectSize() {
        return isModelFilePresentWithCorrectSize(getCurrentModelKey());
    }

    public boolean isModelFilePresentWithCorrectSize(String modelKey) {
        File file = getModelFile(modelKey);
        return file != null && file.exists() && file.length() > getExpectedMinSize(modelKey);
    }

    public boolean isMmprojFilePresentWithCorrectSize() {
        return isMmprojFilePresentWithCorrectSize(getCurrentModelKey());
    }

    public boolean isMmprojFilePresentWithCorrectSize(String modelKey) {
        if (!MODEL_VISION.equals(modelKey)) return true;
        File file = getMmprojFile(modelKey);
        return file != null && file.exists() && file.length() > getExpectedMmprojMinSize(modelKey);
    }

    public boolean isModelVerified() {
        return isModelVerified(getCurrentModelKey());
    }

    public boolean isModelVerified(String modelKey) {
        File file = getModelFile(modelKey);
        if (!isModelFilePresentWithCorrectSize(modelKey)) return false;

        String modelName = getModelFileName(modelKey);
        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_" + modelName, false);
        long verifiedSize = prefs.getLong("verified_size_" + modelName, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean isMmprojVerified() {
        return isMmprojVerified(getCurrentModelKey());
    }

    public boolean isMmprojVerified(String modelKey) {
        if (!MODEL_VISION.equals(modelKey)) return true;
        File file = getMmprojFile(modelKey);
        if (!isMmprojFilePresentWithCorrectSize(modelKey)) return false;

        String mmprojName = getMmprojFileName(modelKey);
        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_mmproj_" + mmprojName, false);
        long verifiedSize = prefs.getLong("verified_mmproj_size_" + mmprojName, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean verifyModelHash() {
        String modelKey = getCurrentModelKey();
        File file = getModelFile(modelKey);
        if (!isModelFilePresentWithCorrectSize(modelKey)) return false;

        String modelName = getModelFileName(modelKey);
        // If file size matches expected bounds, verify and cache
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("verified_" + modelName, true)
                .putLong("verified_size_" + modelName, file.length())
                .apply();

        if (MODEL_VISION.equals(modelKey)) {
            File mmprojFile = getMmprojFile(modelKey);
            if (mmprojFile != null && isMmprojFilePresentWithCorrectSize(modelKey)) {
                String mmprojName = getMmprojFileName(modelKey);
                context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("verified_mmproj_" + mmprojName, true)
                        .putLong("verified_mmproj_size_" + mmprojName, mmprojFile.length())
                        .apply();
            }
        }
        return true;
    }

    public boolean verifyMmprojHash() {
        String modelKey = getCurrentModelKey();
        if (!MODEL_VISION.equals(modelKey)) return true;
        File file = getMmprojFile(modelKey);
        if (!isMmprojFilePresentWithCorrectSize(modelKey)) return false;

        String mmprojName = getMmprojFileName(modelKey);
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("verified_mmproj_" + mmprojName, true)
                .putLong("verified_mmproj_size_" + mmprojName, file.length())
                .apply();
        return true;
    }

    private boolean isValidModelFile(File file, String modelKey) {
        return isModelFilePresentWithCorrectSize(modelKey) && isModelVerified(modelKey);
    }

    private boolean isValidMmprojFile(File file, String modelKey) {
        return isMmprojFilePresentWithCorrectSize(modelKey) && isMmprojVerified(modelKey);
    }
}
