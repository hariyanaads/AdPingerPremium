package com.dbzbanten.adpinger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Lightweight anti-tamper gate. It does not attack or damage a device.
 * The production signing certificate fingerprint is supplied by the remote
 * license document. A repackaged APK signed with another certificate is blocked.
 */
public final class IntegrityGuard {
    private IntegrityGuard() {}

    public static Result verify(Context context, ExpiryConfig.Result remote) {
        try {
            if (remote.packageName != null && !remote.packageName.isEmpty()
                    && !context.getPackageName().equals(remote.packageName)) {
                return Result.fail("Package aplikasi tidak sesuai");
            }

            if (remote.minVersionCode > 0) {
                long version = getVersionCode(context);
                if (version < remote.minVersionCode) {
                    return Result.fail("Versi aplikasi sudah terlalu lama");
                }
            }

            if (remote.signatureSha256 == null || remote.signatureSha256.trim().isEmpty()) {
                // Allows initial deployment before the production fingerprint is entered.
                // Set signature_sha256 in expiry.json to enforce certificate checking.
                return Result.ok("Pemeriksaan sertifikat belum dikunci");
            }

            String actual = getSigningCertificateSha256(context);
            String expected = normalize(remote.signatureSha256);
            if (!expected.equals(actual)) {
                return Result.fail("Integritas aplikasi tidak valid");
            }
            return Result.ok("Integritas aplikasi valid");
        } catch (Exception e) {
            return Result.fail("Pemeriksaan integritas gagal");
        }
    }

    private static long getVersionCode(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= 28) return pi.getLongVersionCode();
        return pi.versionCode;
    }

    private static String getSigningCertificateSha256(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        byte[] cert;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            cert = pi.signingInfo.hasMultipleSigners()
                    ? pi.signingInfo.getApkContentsSigners()[0].toByteArray()
                    : pi.signingInfo.getSigningCertificateHistory()[0].toByteArray();
        } else {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            cert = pi.signatures[0].toByteArray();
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return normalize(toHex(md.digest(cert)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02X:", b & 0xff));
        if (out.length() > 0) out.setLength(out.length() - 1);
        return out.toString();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace(" ", "").replace("-", ":").toUpperCase(Locale.US);
    }

    public static final class Result {
        public final boolean valid;
        public final String message;
        private Result(boolean valid, String message) { this.valid = valid; this.message = message; }
        static Result ok(String m) { return new Result(true, m); }
        static Result fail(String m) { return new Result(false, m); }
    }
}
