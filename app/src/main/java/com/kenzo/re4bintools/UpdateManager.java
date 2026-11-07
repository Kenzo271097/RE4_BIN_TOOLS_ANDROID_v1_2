package com.kenzo.re4bintools;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String RELEASE_API =
        "https://api.github.com/repos/Kenzo271097/RE4_BIN_TOOLS_ANDROID_v1_2/releases/latest";

    private static final String APK_NAME = "RE4.BIN.TOOL.apk";

    private final Activity activity;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    private volatile boolean checking = false;
    private volatile boolean downloading = false;
    private boolean waitingForInstallPermission = false;

    private String downloadUrl;
    private String expectedSha256;
    private String latestVersion;
    private String releaseNotes;

    private ProgressDialog progressDialog;
    private ConnectivityManager.NetworkCallback networkCallback;
    private android.content.BroadcastReceiver networkReceiver;

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkForUpdates(boolean manual) {
        if (checking || downloading) return;

        checking = true;

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                ConnectivityManager cm =
                    (ConnectivityManager) activity.getSystemService(
                        Activity.CONNECTIVITY_SERVICE
                    );

                if (cm == null || cm.getActiveNetworkInfo() == null ||
                    !cm.getActiveNetworkInfo().isConnected()) {

                    if (manual) {
                        activity.runOnUiThread(() ->
                            Toast.makeText(activity,
                                "Tidak ada koneksi internet.",
                                Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                URL url = new URL(RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                    "Accept", "application/vnd.github+json"
                );
                connection.setRequestProperty(
                    "User-Agent", "RE4-BIN-TOOLS-Android"
                );

                if (connection.getResponseCode() != 200) {
                    throw new Exception(
                        "GitHub HTTP " + connection.getResponseCode()
                    );
                }

                StringBuilder response = new StringBuilder();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        response.append(
                            new String(buffer, 0, count, "UTF-8")
                        );
                    }
                }

                JSONObject release =
                    new JSONObject(response.toString());

                latestVersion = release.optString("tag_name", "")
                    .replaceFirst("^[vV]", "");

                releaseNotes = release.optString("body", "");

                JSONArray assets = release.optJSONArray("assets");

                if (assets == null) {
                    throw new Exception("Release tidak memiliki asset.");
                }

                downloadUrl = null;
                expectedSha256 = null;

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);

                    if (APK_NAME.equals(asset.optString("name"))) {
                        downloadUrl =
                            asset.optString("browser_download_url", "");

                        String digest = asset.optString("digest", "");

                        if (digest.startsWith("sha256:")) {
                            expectedSha256 =
                                digest.substring("sha256:".length())
                                    .toLowerCase(Locale.US);
                        }

                        break;
                    }
                }

                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    throw new Exception(
                        "APK " + APK_NAME + " tidak ditemukan di Release."
                    );
                }

                if (compareVersions(
                    latestVersion, BuildConfig.VERSION_NAME
                ) > 0) {

                    activity.runOnUiThread(() -> showUpdateDialog());

                } else if (manual) {

                    activity.runOnUiThread(() ->
                        Toast.makeText(activity,
                            "Aplikasi sudah versi terbaru.",
                            Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                if (manual) {
                    activity.runOnUiThread(() ->
                        new AlertDialog.Builder(activity)
                            .setTitle("Pemeriksaan Update Gagal")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show());
                }
            } finally {
                checking = false;
                if (connection != null) connection.disconnect();
            }
        });
    }

    private int compareVersions(String first, String second) {
        try {
            String[] a = first.split("\\.");
            String[] b = second.split("\\.");

            int length = Math.max(a.length, b.length);

            for (int i = 0; i < length; i++) {
                int x = i < a.length ? Integer.parseInt(a[i]) : 0;
                int y = i < b.length ? Integer.parseInt(b[i]) : 0;

                if (x != y) return Integer.compare(x, y);
            }
        } catch (Exception ignored) {
        }

        return first.compareTo(second);
    }

    private void showUpdateDialog() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        String message =
            "Versi terpasang: " + BuildConfig.VERSION_NAME +
            "\nVersi terbaru: " + latestVersion +
            "\n\n" + releaseNotes;

        new AlertDialog.Builder(activity)
            .setTitle("Update RE4 BIN TOOLS Tersedia")
            .setMessage(message)
            .setNegativeButton("NANTI", null)
            .setPositiveButton("DOWNLOAD", (dialog, which) -> {
                if (expectedSha256 == null ||
                    expectedSha256.length() != 64) {

                    new AlertDialog.Builder(activity)
                        .setTitle("Verifikasi Tidak Tersedia")
                        .setMessage(
                            "GitHub belum menyediakan SHA-256 untuk APK " +
                            "ini. Download dibatalkan agar integritas APK " +
                            "tidak dianggap terverifikasi."
                        )
                        .setPositiveButton("OK", null)
                        .show();
                    return;
                }

                downloadUpdate();
            })
            .show();
    }

    private void downloadUpdate() {
        if (downloading) return;
        downloading = true;

        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Mengunduh Update");
        progressDialog.setMessage("Menyiapkan download...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setProgress(0);
        progressDialog.show();

        executor.execute(() -> {
            HttpURLConnection connection = null;
            File apk = new File(activity.getCacheDir(), "re4_update.apk");

            try {
                connection = (HttpURLConnection)
                    new URL(downloadUrl).openConnection();

                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty(
                    "User-Agent", "RE4-BIN-TOOLS-Android"
                );

                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    throw new Exception(
                        "Download gagal. HTTP " + responseCode
                    );
                }

                int total = connection.getContentLength();

                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                try (
                    InputStream in = new BufferedInputStream(
                        connection.getInputStream()
                    );
                    BufferedOutputStream out = new BufferedOutputStream(
                        new FileOutputStream(apk)
                    )
                ) {
                    byte[] buffer = new byte[8192];
                    int count;
                    int downloaded = 0;

                    while ((count = in.read(buffer)) != -1) {
                        digest.update(buffer, 0, count);
                        out.write(buffer, 0, count);
                        downloaded += count;

                        if (total > 0) {
                            final int percent =
                                (int) (downloaded * 100L / total);

                            activity.runOnUiThread(() -> {
                                if (progressDialog != null) {
                                    progressDialog.setProgress(percent);
                                    progressDialog.setMessage(
                                        "Mengunduh... " + percent + "%"
                                    );
                                }
                            });
                        }
                    }
                }

                StringBuilder actualHash = new StringBuilder();

                for (byte value : digest.digest()) {
                    actualHash.append(
                        String.format(Locale.US, "%02x", value & 0xff)
                    );
                }

                if (!actualHash.toString().equalsIgnoreCase(
                    expectedSha256
                )) {
                    apk.delete();
                    throw new Exception(
                        "SHA-256 tidak cocok. APK ditolak."
                    );
                }

                SharedPreferences prefs =
                    activity.getSharedPreferences(
                        "re4_updater", Activity.MODE_PRIVATE
                    );

                prefs.edit()
                    .putBoolean("verified_apk", true)
                    .apply();

                activity.runOnUiThread(() -> {
                    closeProgress();
                    Toast.makeText(activity,
                        "Download dan verifikasi berhasil.",
                        Toast.LENGTH_LONG).show();
                    installPendingIfAllowed();
                });

            } catch (Exception e) {
                apk.delete();

                activity.runOnUiThread(() -> {
                    closeProgress();

                    new AlertDialog.Builder(activity)
                        .setTitle("Update Gagal")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
                });

            } finally {
                downloading = false;
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void closeProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    public void installPendingIfAllowed() {
        File apk = new File(activity.getCacheDir(), "re4_update.apk");

        SharedPreferences prefs =
            activity.getSharedPreferences(
                "re4_updater", Activity.MODE_PRIVATE
            );

        if (!apk.exists() ||
            !prefs.getBoolean("verified_apk", false)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 26 &&
            !activity.getPackageManager().canRequestPackageInstalls()) {

            new AlertDialog.Builder(activity)
                .setTitle("Izin Instalasi Diperlukan")
                .setMessage(
                    "Izinkan RE4 BIN TOOLS memasang update melalui " +
                    "pengaturan Android."
                )
                .setNegativeButton("BATAL", null)
                .setPositiveButton("BUKA PENGATURAN", (d, w) -> {
                    waitingForInstallPermission = true;
                    Intent intent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())
                    );
                    activity.startActivity(intent);
                })
                .show();

            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                uri, "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(activity,
                "Installer gagal dibuka: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }


    public void resumeAfterPermission() {
        if (!waitingForInstallPermission) return;

        waitingForInstallPermission = false;

        if (Build.VERSION.SDK_INT < 26 ||
            activity.getPackageManager().canRequestPackageInstalls()) {
            installPendingIfAllowed();
        }
    }

    public void startNetworkMonitoring() {
        ConnectivityManager cm =
            (ConnectivityManager) activity.getSystemService(
                Activity.CONNECTIVITY_SERVICE
            );

        if (cm == null) return;

        if (Build.VERSION.SDK_INT >= 24) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    activity.runOnUiThread(() ->
                        checkForUpdates(false));
                }
            };

            try {
                cm.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }

        } else {
            networkReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(
                    android.content.Context context,
                    Intent intent
                ) {
                    checkForUpdates(false);
                }
            };

            activity.registerReceiver(
                networkReceiver,
                new android.content.IntentFilter(
                    ConnectivityManager.CONNECTIVITY_ACTION
                )
            );
        }
    }

    public void stopNetworkMonitoring() {
        ConnectivityManager cm =
            (ConnectivityManager) activity.getSystemService(
                Activity.CONNECTIVITY_SERVICE
            );

        if (Build.VERSION.SDK_INT >= 24 &&
            networkCallback != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }

        if (networkReceiver != null) {
            try {
                activity.unregisterReceiver(networkReceiver);
            } catch (Exception ignored) {
            }
            networkReceiver = null;
        }
    }

    public void shutdown() {
        stopNetworkMonitoring();
        closeProgress();
        executor.shutdownNow();
    }
}
