package com.lsan.sec.plugin;

import android.content.Context;
import android.net.Uri;

import com.lsan.sec.util.Prefs;
import com.lsan.sec.util.Text;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 功能包（.mpk）导入器。
 *
 * 功能包 = zip，内含：
 *   manifest.json  必需，声明 id / name / version / desc / author
 *   rules.json     可选，声明式匹配规则数组
 *
 * 设计约束：
 *  - 功能包只允许"声明式规则"，不执行任何外来代码，杜绝导入即被控。
 *  - 导入过程完全在本地完成，不上传、不联网。
 */
public final class PluginLoader {

    public static final String EXT = ".mpk";
    private static final int MAX_ENTRY_BYTES = 512 * 1024;
    private static final int MAX_RULES = 200;
    private static final int MAX_RULE_TEXT = 200;

    private PluginLoader() {}

    public static class ImportResult {
        public boolean ok;
        public String message = "";
        public String pluginId = "";
        public String pluginName = "";
        public int ruleCount;
    }

    /** 从已选择的文件 Uri 导入功能包。 */
    public static ImportResult importFrom(Context ctx, Uri uri) {
        Prefs.get().init(ctx);
        ImportResult res = new ImportResult();
        String manifestJson = null;
        String rulesJson = null;

        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                res.message = "无法读取所选文件";
                return res;
            }
            ZipInputStream zis = new ZipInputStream(in);
            ZipEntry e;
            int entries = 0;
            while ((e = zis.getNextEntry()) != null) {
                if (++entries > 64) {
                    res.message = "功能包条目过多，已拒绝";
                    return res;
                }
                String name = e.getName();
                if (e.isDirectory()) continue;
                if (!name.equals("manifest.json") && !name.equals("rules.json")) {
                    continue; // 忽略其它文件，功能包不承载代码
                }
                byte[] data = readLimited(zis, MAX_ENTRY_BYTES);
                if (data == null) {
                    res.message = "功能包条目过大，已拒绝";
                    return res;
                }
                String text = new String(data, "UTF-8");
                if (name.equals("manifest.json")) manifestJson = text;
                else rulesJson = text;
            }
            zis.close();
        } catch (Exception ex) {
            res.message = "不是有效的功能包（解压失败）";
            return res;
        }

        if (manifestJson == null) {
            res.message = "功能包缺少 manifest.json";
            return res;
        }

        JSONObject manifest;
        JSONArray rules = new JSONArray();
        try {
            manifest = new JSONObject(manifestJson);
        } catch (Exception e) {
            res.message = "manifest.json 格式错误";
            return res;
        }

        String id = Text.nz(manifest.optString("id", "")).trim();
        String name = Text.nz(manifest.optString("name", "")).trim();
        String version = Text.nz(manifest.optString("version", "1.0")).trim();
        String author = Text.nz(manifest.optString("author", "")).trim();
        String desc = Text.nz(manifest.optString("desc", "")).trim();

        if (!id.matches("[a-z0-9_.]{3,64}")) {
            res.message = "功能包 id 非法（只允许小写字母、数字、下划线、点，3~64 位）";
            return res;
        }
        if (name.isEmpty()) {
            res.message = "功能包缺少 name";
            return res;
        }
        if (id.equals("engine_core") || id.equals("jump_guard") || id.equals("clip_guard")
                || id.equals("screen_guard") || id.equals("collusion_guard")
                || id.equals("notif_guard") || id.equals("perm_audit") || id.equals("event_center")) {
            res.message = "功能包 id 与内置模块冲突";
            return res;
        }

        if (rulesJson != null) {
            try {
                JSONArray raw = new JSONArray(rulesJson);
                for (int i = 0; i < raw.length(); i++) {
                    JSONObject o = raw.optJSONObject(i);
                    if (o == null) continue;
                    if (rules.length() >= MAX_RULES) {
                        res.message = "规则数量超过上限 " + MAX_RULES;
                        return res;
                    }
                    // 严格校验：作用域、动作、文本长度
                    String scope = com.lsan.sec.core.Rules.normalizeScope(o.optString("scope", ""));
                    if (scope.isEmpty()) continue;
                    String act = o.optString("action", "alert");
                    if (!act.equals("block") && !act.equals("alert") && !act.equals("allow")) continue;
                    JSONObject m = o.optJSONObject("match");
                    if (m != null) {
                        String kw = m.optString("keyword", "");
                        String rx = m.optString("regex", "");
                        if (kw.length() > MAX_RULE_TEXT || rx.length() > MAX_RULE_TEXT) {
                            res.message = "单条规则文本过长，已拒绝";
                            return res;
                        }
                        if (!rx.isEmpty()) {
                            try {
                                java.util.regex.Pattern.compile(rx);
                            } catch (Exception e) {
                                res.message = "规则正则非法：" + rx;
                                return res;
                            }
                        }
                    }
                    rules.put(o);
                }
            } catch (Exception e) {
                res.message = "rules.json 格式错误";
                return res;
            }
        }

        // 落盘保存功能包内容（本地私有目录）
        try {
            File dir = new File(ctx.getFilesDir(), "plugins/" + id);
            if (!dir.exists() && !dir.mkdirs()) {
                res.message = "无法创建功能包目录";
                return res;
            }
            writeFile(new File(dir, "manifest.json"), manifestJson);
            writeFile(new File(dir, "rules.json"), rulesJson == null ? "[]" : rulesJson);
        } catch (Exception e) {
            res.message = "功能包保存失败";
            return res;
        }

        // 注册到模块表
        try {
            JSONArray arr = new JSONArray(Prefs.get().pluginsJson());
            JSONArray next = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id"))) {
                    next.put(buildEntry(id, name, version, author, desc, rules));
                    replaced = true;
                } else {
                    next.put(o);
                }
            }
            if (!replaced) next.put(buildEntry(id, name, version, author, desc, rules));
            Prefs.get().setPluginsJson(next.toString());
            Prefs.get().setModuleEnabled(id, true);
        } catch (Exception e) {
            res.message = "功能包注册失败";
            return res;
        }

        res.ok = true;
        res.pluginId = id;
        res.pluginName = name;
        res.ruleCount = rules.length();
        res.message = "已导入：" + name + "（" + rules.length() + " 条规则）";
        return res;
    }

    private static JSONObject buildEntry(String id, String name, String version, String author,
                                         String desc, JSONArray rules) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("version", version);
        o.put("author", author);
        o.put("desc", desc);
        o.put("defaultOn", true);
        o.put("requires", "自定义规则包");
        o.put("rules", rules);
        o.put("importedAt", System.currentTimeMillis());
        return o;
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) return null;
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static void writeFile(File f, String text) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(text.getBytes("UTF-8"));
        }
    }


    /** 应用私有外部目录中的待导入功能包目录（无需任何存储权限）。 */
    public static File incomingDir(Context ctx) {
        File dir = ctx.getExternalFilesDir("incoming");
        if (dir == null) dir = new File(ctx.getFilesDir(), "incoming");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** 列出待导入目录下的 .mpk 文件。 */
    public static List<File> pendingPacks(Context ctx) {
        List<File> out = new ArrayList<>();
        File dir = incomingDir(ctx);
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(EXT)) out.add(f);
            }
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** 从本地文件路径导入（用于私有目录批量导入）。 */
    public static ImportResult importFromFile(Context ctx, File f) {
        if (f == null || !f.exists()) {
            ImportResult r = new ImportResult();
            r.message = "文件不存在";
            return r;
        }
        return importFrom(ctx, Uri.fromFile(f));
    }

    /** 已导入功能包列表（id / name / version / rule 数）。 */
    public static List<JSONObject> list(Context ctx) {
        Prefs.get().init(ctx);
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get().pluginsJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean remove(Context ctx, String id) {
        Prefs.get().init(ctx);
        try {
            JSONArray arr = new JSONArray(Prefs.get().pluginsJson());
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (!id.equals(o.optString("id"))) next.put(o);
            }
            Prefs.get().setPluginsJson(next.toString());
            Prefs.get().setModuleEnabled(id, false);
            File dir = new File(ctx.getFilesDir(), "plugins/" + id);
            deleteTree(dir);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** 导出一个功能包到指定 Uri（备份 / 分享给其它设备）。 */
    public static boolean exportTo(Context ctx, String id, Uri target) {
        try {
            File dir = new File(ctx.getFilesDir(), "plugins/" + id);
            File mf = new File(dir, "manifest.json");
            File rf = new File(dir, "rules.json");
            if (!mf.exists()) return false;
            try (java.io.OutputStream os = ctx.getContentResolver().openOutputStream(target)) {
                if (os == null) return false;
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os);
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(readAll(mf));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("rules.json"));
                zos.write(readAll(rf));
                zos.closeEntry();
                zos.finish();
                zos.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readAll(File f) throws Exception {
        if (!f.exists()) return "[]".getBytes("UTF-8");
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }


    /** 导入内置示例功能包（用于验证导入链路，无需选择文件）。 */
    public static ImportResult importBuiltinSample(Context ctx) {
        try {
            File dir = new File(ctx.getCacheDir(), "sample");
            if (!dir.exists() && !dir.mkdirs()) {
                ImportResult r = new ImportResult();
                r.message = "无法创建临时目录";
                return r;
            }
            File mpk = new File(dir, "sample.mpk");
            try (java.io.OutputStream os = new FileOutputStream(mpk)) {
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(os);
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(sampleManifest().getBytes("UTF-8"));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("rules.json"));
                zos.write(sampleRules().getBytes("UTF-8"));
                zos.closeEntry();
                zos.finish();
                zos.close();
            }
            return importFrom(ctx, Uri.fromFile(mpk));
        } catch (Exception e) {
            ImportResult r = new ImportResult();
            r.message = "示例包导入失败：" + e.getMessage();
            return r;
        }
    }

    /** 一个可直接导入的示例功能包内容（用于"导出示例"按钮）。 */
    public static String sampleManifest() {
        return "{\n"
                + "  \"id\": \"demo.custom_rules\",\n"
                + "  \"name\": \"示例：验证码外泄防护\",\n"
                + "  \"version\": \"1.0\",\n"
                + "  \"author\": \"Murphy\",\n"
                + "  \"desc\": \"演示功能包格式：拦截非白名单应用转发验证码类文本\"\n"
                + "}";
    }

    public static String sampleRules() {
        return "[\n"
                + "  {\n"
                + "    \"id\": \"otp-forward\",\n"
                + "    \"scope\": \"clip\",\n"
                + "    \"action\": \"block\",\n"
                + "    \"match\": { \"sensitive\": [\"otp\", \"验证码\"] },\n"
                + "    \"note\": \"剪贴板出现验证码时阻止离开当前应用\"\n"
                + "  }\n"
                + "]";
    }
}
