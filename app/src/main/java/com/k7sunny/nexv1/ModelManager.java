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
        return isVisionModel(getCurrentModelKey());
    }

    public boolean isVisionModel(String modelKey) {
        return isMmprojDownloaded(modelKey);
    }

    public String getModelDisplayName(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "Nex Pro (2B)";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "Nex Ultra (4B)";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "Nex Vision (Legacy)";
        } else {
            return "Nex Fast (0.8B)";
        }
    }

    public String getCurrentModelDisplayName() {
        return getModelDisplayName(getCurrentModelKey());
    }

    public String getModelFileName(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "Qwen_Qwen3.5-2B-Q4_K_M.gguf";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "Qwen_Qwen3.5-4B-Q4_K_M.gguf";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf";
        } else {
            return "Qwen_Qwen3.5-0.8B-Q4_K_M.gguf";
        }
    }

    public String getModelSize(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "~1.4GB";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "~3.0GB";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "~2.7GB";
        } else {
            return "~580MB";
        }
    }

    public String getModelDescription(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "Smart conversational AI with reasoning & coding.";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "Deep reasoning, advanced coding & logic.";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "Offline image analysis & OCR (legacy).";
        } else {
            return "Ultra-fast responses & instant summaries.";
        }
    }

    public int getModelIconRes(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return R.drawable.app_icon;
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return R.drawable.ic_persona;
        } else if (MODEL_VISION.equals(modelKey)) {
            return R.drawable.ic_vision;
        } else {
            return R.drawable.ic_bolt;
        }
    }

    public String getModelTag(String modelKey) {
        if (MODEL_ULTRA.equals(modelKey)) {
            return "REASONING";
        }
        return null;
    }

    public java.util.List<ModelItem> getAvailableModels() {
        java.util.List<ModelItem> items = new java.util.ArrayList<>();
        items.add(new ModelItem(MODEL_FAST, getModelDisplayName(MODEL_FAST), getModelSize(MODEL_FAST), getModelDescription(MODEL_FAST), getModelIconRes(MODEL_FAST), getModelTag(MODEL_FAST)));
        items.add(new ModelItem(MODEL_PRO, getModelDisplayName(MODEL_PRO), getModelSize(MODEL_PRO), getModelDescription(MODEL_PRO), getModelIconRes(MODEL_PRO), getModelTag(MODEL_PRO)));
        items.add(new ModelItem(MODEL_ULTRA, getModelDisplayName(MODEL_ULTRA), getModelSize(MODEL_ULTRA), getModelDescription(MODEL_ULTRA), getModelIconRes(MODEL_ULTRA), getModelTag(MODEL_ULTRA)));
        return items;
    }

    public String getMmprojFileName(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "mmproj-Qwen_Qwen3.5-2B-f16.gguf";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "mmproj-Qwen_Qwen3.5-4B-f16.gguf";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf";
        } else {
            return "mmproj-Qwen_Qwen3.5-0.8B-f16.gguf";
        }
    }

    public String getMmprojSize(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "~668MB";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "~672MB";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "~1.3GB";
        } else {
            return "~205MB";
        }
    }

    public String getModelUrl(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/Qwen_Qwen3.5-2B-Q4_K_M.gguf?download=true";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_K_M.gguf?download=true";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf?download=true";
        } else {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/Qwen_Qwen3.5-0.8B-Q4_K_M.gguf?download=true";
        }
    }

    public String getMmprojUrl(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-2B-f16.gguf?download=true";
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-4B-f16.gguf?download=true";
        } else if (MODEL_VISION.equals(modelKey)) {
            return "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf?download=true";
        } else {
            return "https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-0.8B-f16.gguf?download=true";
        }
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
            return 1000L * 1024L * 1024L; // ~1.0 GB
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return 2200L * 1024L * 1024L; // ~2.2 GB
        } else if (MODEL_VISION.equals(modelKey)) {
            return 1800L * 1024L * 1024L; // ~1.8 GB
        } else {
            return 400L * 1024L * 1024L; // ~400 MB
        }
    }

    public long getExpectedMmprojMinSize(String modelKey) {
        if (MODEL_PRO.equals(modelKey)) {
            return 400L * 1024L * 1024L; // ~400 MB
        } else if (MODEL_ULTRA.equals(modelKey)) {
            return 400L * 1024L * 1024L; // ~400 MB
        } else if (MODEL_VISION.equals(modelKey)) {
            return 500L * 1024L * 1024L; // ~500 MB
        } else {
            return 150L * 1024L * 1024L; // ~150 MB
        }
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
        return getModelFile(getCurrentModelKey());
    }

    public File getModelFile(String modelKey) {
        File preferred = new File(getModelDirectory(), getModelFileName(modelKey));
        if (preferred.exists()) {
            return preferred;
        }
        // Fallback for legacy filenames if previously downloaded
        if (MODEL_FAST.equals(modelKey)) {
            File legacy0 = new File(getModelDirectory(), "Qwen3.5-0.8B-Instruct-Q4_K_M.gguf");
            if (legacy0.exists()) return legacy0;
            File legacy1 = new File(getModelDirectory(), "qwen2.5-0.5b-instruct-q4_k_m.gguf");
            if (legacy1.exists()) return legacy1;
            File legacy2 = new File(getModelDirectory(), "qwen2.5-0.5b-instruct.gguf");
            if (legacy2.exists()) return legacy2;
        } else if (MODEL_PRO.equals(modelKey)) {
            File legacy0 = new File(getModelDirectory(), "Qwen3.5-2B-Instruct-Q4_K_M.gguf");
            if (legacy0.exists()) return legacy0;
            File legacy1 = new File(getModelDirectory(), "qwen2.5-1.5b-instruct-q4_k_m.gguf");
            if (legacy1.exists()) return legacy1;
            File legacy2 = new File(getModelDirectory(), "qwen2.5-1.5b-instruct.gguf");
            if (legacy2.exists()) return legacy2;
        } else if (MODEL_ULTRA.equals(modelKey)) {
            File legacy0 = new File(getModelDirectory(), "Qwen3.5-4B-Instruct-Q4_K_M.gguf");
            if (legacy0.exists()) return legacy0;
            File legacy1 = new File(getModelDirectory(), "llama-3.2-3b-instruct-q4_k_m.gguf");
            if (legacy1.exists()) return legacy1;
            File legacy2 = new File(getModelDirectory(), "llama-3.2-3b-instruct.gguf");
            if (legacy2.exists()) return legacy2;
        } else if (MODEL_VISION.equals(modelKey)) {
            File legacy = new File(getModelDirectory(), "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf");
            if (legacy.exists()) return legacy;
        }
        return preferred;
    }

    public File getMmprojFile() {
        return getMmprojFile(getCurrentModelKey());
    }

    public File getMmprojFile(String modelKey) {
        String mmprojName = getMmprojFileName(modelKey);
        if (mmprojName == null) return null;
        return new File(getModelDirectory(), mmprojName);
    }

    public boolean isModelDownloaded() {
        return isModelDownloaded(getCurrentModelKey());
    }

    public boolean isModelDownloaded(String modelKey) {
        return isValidModelFile(getModelFile(modelKey), modelKey);
    }

    public boolean isMmprojDownloaded() {
        return isMmprojDownloaded(getCurrentModelKey());
    }

    public boolean isMmprojDownloaded(String modelKey) {
        File file = getMmprojFile(modelKey);
        return file != null && isValidMmprojFile(file, modelKey);
    }

    public boolean isMmprojMissing() {
        return isMmprojMissing(getCurrentModelKey());
    }

    public boolean isMmprojMissing(String modelKey) {
        File file = getMmprojFile(modelKey);
        return file == null || !file.exists() || file.length() < getExpectedMmprojMinSize(modelKey);
    }

    public void cleanupCorruptedModel() {
        String modelKey = getCurrentModelKey();
        File file = getModelFile(modelKey);
        if (file.exists()) {
            String modelName = file.getName();
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

        File mmprojFile = getMmprojFile(modelKey);
        if (mmprojFile != null && mmprojFile.exists()) {
            String mmprojName = mmprojFile.getName();
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

    public boolean deleteModel(String modelKey) {
        boolean success = true;
        File file = getModelFile(modelKey);
        if (file.exists()) {
            String modelName = file.getName();
            success = file.delete();
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("verified_" + modelName)
                    .remove("verified_size_" + modelName)
                    .apply();
        }
        // Also delete legacy alternative files if present to free storage
        if (MODEL_FAST.equals(modelKey)) {
            String[] legacyNames = {"Qwen3.5-0.8B-Instruct-Q4_K_M.gguf", "qwen2.5-0.5b-instruct-q4_k_m.gguf", "qwen2.5-0.5b-instruct.gguf"};
            for (String name : legacyNames) {
                File legacy = new File(getModelDirectory(), name);
                if (legacy.exists()) {
                    legacy.delete();
                    context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE).edit()
                            .remove("verified_" + name)
                            .remove("verified_size_" + name)
                            .apply();
                }
            }
        } else if (MODEL_PRO.equals(modelKey)) {
            String[] legacyNames = {"Qwen3.5-2B-Instruct-Q4_K_M.gguf", "qwen2.5-1.5b-instruct-q4_k_m.gguf", "qwen2.5-1.5b-instruct.gguf"};
            for (String name : legacyNames) {
                File legacy = new File(getModelDirectory(), name);
                if (legacy.exists()) {
                    legacy.delete();
                    context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE).edit()
                            .remove("verified_" + name)
                            .remove("verified_size_" + name)
                            .apply();
                }
            }
        } else if (MODEL_ULTRA.equals(modelKey)) {
            String[] legacyNames = {"Qwen3.5-4B-Instruct-Q4_K_M.gguf", "llama-3.2-3b-instruct-q4_k_m.gguf", "llama-3.2-3b-instruct.gguf"};
            for (String name : legacyNames) {
                File legacy = new File(getModelDirectory(), name);
                if (legacy.exists()) {
                    legacy.delete();
                    context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE).edit()
                            .remove("verified_" + name)
                            .remove("verified_size_" + name)
                            .apply();
                }
            }
        }
        File mmprojFile = getMmprojFile(modelKey);
        if (mmprojFile != null && mmprojFile.exists()) {
            String mmprojName = mmprojFile.getName();
            boolean mmprojDeleted = mmprojFile.delete();
            success = success && mmprojDeleted;
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("verified_mmproj_" + mmprojName)
                    .remove("verified_mmproj_size_" + mmprojName)
                    .apply();
        }
        return success;
    }

    public boolean isModelFileCorrupted() {
        String modelKey = getCurrentModelKey();
        File file = getModelFile(modelKey);
        boolean modelCorrupted = file.exists() && (!isModelFilePresentWithCorrectSize(modelKey) || !isModelVerified(modelKey));
        File mmprojFile = getMmprojFile(modelKey);
        boolean mmprojCorrupted = mmprojFile != null && mmprojFile.exists() && (!isMmprojFilePresentWithCorrectSize(modelKey) || !isMmprojVerified(modelKey));
        return modelCorrupted || mmprojCorrupted;
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

        // If the language model is already downloaded and verified, download mmproj instead
        if (isModelFilePresentWithCorrectSize(modelKey) && isModelVerified(modelKey)) {
            return downloadMmproj();
        }

        File modelFile = new File(getModelDirectory(), getModelFileName(modelKey));
        if (modelFile.exists()) {
            try {
                modelFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String url = getModelUrl(modelKey);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Nex AI Model (" + getModelDisplayName(modelKey) + ")")
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
                .setTitle("Downloading Vision Projector (" + getModelDisplayName(modelKey) + ")")
                .setDescription("Preparing multimodal image reasoning engine...")
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
        File file = getMmprojFile(modelKey);
        return file != null && file.exists() && file.length() > getExpectedMmprojMinSize(modelKey);
    }

    public boolean isModelVerified() {
        return isModelVerified(getCurrentModelKey());
    }

    public boolean isModelVerified(String modelKey) {
        File file = getModelFile(modelKey);
        if (!isModelFilePresentWithCorrectSize(modelKey)) return false;

        String modelName = file.getName();
        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_" + modelName, false);
        long verifiedSize = prefs.getLong("verified_size_" + modelName, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean isMmprojVerified() {
        return isMmprojVerified(getCurrentModelKey());
    }

    public boolean isMmprojVerified(String modelKey) {
        File file = getMmprojFile(modelKey);
        if (!isMmprojFilePresentWithCorrectSize(modelKey)) return false;

        String mmprojName = file.getName();
        android.content.SharedPreferences prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        boolean verified = prefs.getBoolean("verified_mmproj_" + mmprojName, false);
        long verifiedSize = prefs.getLong("verified_mmproj_size_" + mmprojName, -1);

        return verified && verifiedSize == file.length();
    }

    public boolean verifyModelHash() {
        String modelKey = getCurrentModelKey();
        boolean modelOk = false;
        File file = getModelFile(modelKey);
        if (isModelFilePresentWithCorrectSize(modelKey) && isGgufHeaderValid(file)) {
            String modelName = file.getName();
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("verified_" + modelName, true)
                    .putLong("verified_size_" + modelName, file.length())
                    .apply();
            modelOk = true;
        }

        File mmprojFile = getMmprojFile(modelKey);
        if (mmprojFile != null && isMmprojFilePresentWithCorrectSize(modelKey) && isGgufHeaderValid(mmprojFile)) {
            String mmprojName = mmprojFile.getName();
            context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("verified_mmproj_" + mmprojName, true)
                    .putLong("verified_mmproj_size_" + mmprojName, mmprojFile.length())
                    .apply();
        }
        return modelOk || isMmprojDownloaded(modelKey);
    }

    public boolean verifyMmprojHash() {
        String modelKey = getCurrentModelKey();
        File file = getMmprojFile(modelKey);
        if (file == null || !isMmprojFilePresentWithCorrectSize(modelKey) || !isGgufHeaderValid(file)) return false;

        String mmprojName = file.getName();
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("verified_mmproj_" + mmprojName, true)
                .putLong("verified_mmproj_size_" + mmprojName, file.length())
                .apply();
        return true;
    }

    public static boolean isGgufHeaderValid(File file) {
        if (file == null || !file.exists() || file.length() < 4) return false;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            return read == 4 && header[0] == 'G' && header[1] == 'G' && header[2] == 'U' && header[3] == 'F';
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidModelFile(File file, String modelKey) {
        return isModelFilePresentWithCorrectSize(modelKey) && isGgufHeaderValid(file) && isModelVerified(modelKey);
    }

    private boolean isValidMmprojFile(File file, String modelKey) {
        return isMmprojFilePresentWithCorrectSize(modelKey) && isGgufHeaderValid(file) && isMmprojVerified(modelKey);
    }
}
