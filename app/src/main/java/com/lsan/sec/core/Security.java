package com.lsan.sec.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行时安全自检：防二次打包、防注入、防调试、防 Hook。
 *
 * 设计原则：
 *  - 只做"检测 + 降级 + 留痕"，不直接崩溃（避免误伤正常用户）；
 *  - 检测项全部本地完成，不联网、不上报；
 *  - 与 R8 混淆配合，提高静态分析成本。
 */
public final class Security {

    /** 发布证书 SHA-256（自签名证书指纹，二次打包必然变化）。 */
    private static final String EXPECTED_CERT_SHA256 =
            "602091 46B2C64FA0270956C7BE33F1EB7365D64F0806388773C9136E672985C0".replace(" ", "");

    /** 已知 Hook / 注入框架的特征串（小写）。 */
    private static final String[] HOOK_MARKERS = {
            "frida", "gum-js-loop", "gmain", "substrate", "xposed", "lsposed",
            "edxposed", "sandhook", "yuki", "riru", "pine", "epic", "whale",
            "virtualapp", "double-fish", "vapp"
    };

    private Security() {}

    public static class Report {
        public boolean tampered;      // 签名不符（二次打包）
        public boolean debugged;      // 调试器附加
        public boolean hooked;        // Hook 框架痕迹
        public boolean emulator;      // 模拟器
        public String certSha256 = "";
        public final List<String> notes = new ArrayList<>();

        public boolean risky() {
            return tampered || debugged || hooked || emulator;
        }

        public String summary() {
            if (!risky()) return "完整性校验通过";
            StringBuilder sb = new StringBuilder();
            if (tampered) sb.append("签名校验失败 ");
            if (debugged) sb.append("检测到调试器 ");
            if (hooked) sb.append("检测到 Hook 框架 ");
            if (emulator) sb.append("运行于模拟器 ");
            return sb.toString().trim();
        }
    }

    public static Report audit(Context ctx) {
        Report r = new Report();
        try {
            r.certSha256 = certSha256(ctx);
            if (!EXPECTED_CERT_SHA256.isEmpty()
                    && !EXPECTED_CERT_SHA256.equalsIgnoreCase(r.certSha256)) {
                r.tampered = true;
                r.notes.add("安装包签名与发布签名不一致，可能被二次打包");
            }
        } catch (Exception e) {
            r.notes.add("签名读取失败：" + e.getClass().getSimpleName());
        }

        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                r.debugged = true;
                r.notes.add("调试器已连接");
            }
            if (tracerPid() != 0) {
                r.debugged = true;
                r.notes.add("进程被 ptrace 附加（TracerPid≠0）");
            }
        } catch (Exception ignored) {
        }

        try {
            String hits = scanMaps();
            if (!hits.isEmpty()) {
                r.hooked = true;
                r.notes.add("内存映射命中注入特征：" + hits);
            }
        } catch (Exception ignored) {
        }

        try {
            if (isEmulator()) {
                r.emulator = true;
                r.notes.add("设备特征符合模拟器");
            }
        } catch (Exception ignored) {
        }

        return r;
    }

    private static int tracerPid() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/status")))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    return Integer.parseInt(line.substring(10).trim());
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String scanMaps() {
        StringBuilder hits = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/maps")))) {
            String line;
            int scanned = 0;
            while ((line = br.readLine()) != null && scanned < 20000) {
                scanned++;
                String low = line.toLowerCase();
                for (String m : HOOK_MARKERS) {
                    if (low.contains(m)) {
                        if (hits.indexOf(m) < 0) {
                            if (hits.length() > 0) hits.append(',');
                            hits.append(m);
                        }
                        break;
                    }
                }
                if (hits.length() > 40) break;
            }
        } catch (Exception ignored) {
        }
        return hits.toString();
    }

    private static boolean isEmulator() {
        String fp = nz(android.os.Build.FINGERPRINT).toLowerCase();
        String model = nz(android.os.Build.MODEL).toLowerCase();
        String hardware = nz(android.os.Build.HARDWARE).toLowerCase();
        String product = nz(android.os.Build.PRODUCT).toLowerCase();
        return fp.startsWith("generic") || fp.contains("test-keys")
                || model.contains("emulator") || model.contains("sdk_gphone")
                || hardware.contains("goldfish") || hardware.contains("ranchu")
                || product.contains("sdk") || product.contains("emulator")
                || new File("/dev/socket/qemud").exists()
                || new File("/dev/qemu_pipe").exists();
    }

    /** 当前 APK 的签名证书 SHA-256（十六进制大写）。 */
    public static String certSha256(Context ctx) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo pi;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pi = pm.getPackageInfo(ctx.getPackageName(),
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            Signature[] sigs = pi.signingInfo == null ? null
                    : pi.signingInfo.getApkContentsSigners();
            return hex(sigs);
        }
        pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
        return hex(pi.signatures);
    }

    private static String hex(Signature[] sigs) throws Exception {
        if (sigs == null || sigs.length == 0) return "";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(sigs[0].toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /** 安装包自身的 SHA-256（用于完整性留痕，可与发布哈希比对）。 */
    public static String apkSha256(Context ctx) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(ctx.getPackageCodePath())) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
