package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 确定性主题包 ZIP 写出器（theme-seed-export §7.1 / Q5）——
 * {@code scripts/build-seed-package.mjs} 的逐规则服务端移植：
 * <ul>
 *   <li><b>确定性容器</b>：条目按遍历序写入（目录树 = 每层名称字典序的深度优先，与 CLI 的
 *       {@code readdirSync().sort()} 同序）、固定 DOS 时间戳（2026-01-01 00:00）、UTF-8 文件名、
 *       仅当压缩更小才用 deflate（level 9 raw）——内容未变则重打包字节一致（D4）；与 CLI 输出
 *       <b>逐项一致</b>（条目序/元数据/内容，Q5 夹具测试钉死）。deflate 位流本身除外：
 *       Node 附带 Chromium 的 zlib 分叉，与 JDK 的原版 zlib 在同参数下匹配选择不同——
 *       跨工具位流一致不是可守护契约（决议见计划 §11）。</li>
 *   <li><b>开发工件排除</b>：依赖/源码目录、source map、打包器/编辑器杂物——与 CLI 同一清单。
 *       后缀排除不适用于 {@code seed/content/assets/**}（内容种子媒体可以合法携带 {@code .zip}
 *       附件——install-flow D28；杂散嵌套包的原始排除目的只针对渲染器目录树）。</li>
 *   <li><b>JSON-only 预检</b>：构成目录（pages/shapes/seed/i18n）只许 {@code .json}，
 *       唯一例外 {@code seed/content/assets/**}（与 {@code ThemeInstallValidator} 同规则）。</li>
 * </ul>
 * 纯静态、无 IO 之外的状态；打包编排（模式分派、清单更新、大小预算）属 {@code ThemeSeedExportService}。
 *
 * @author PONY
 */
public final class SeedPackageWriter {

    /**
     * 排除目录（CLI EXCLUDED_DIRS）
     */
    private static final Set<String> EXCLUDED_DIRS = Set.of("node_modules", "_src", ".git", ".idea", ".vscode");
    /**
     * 排除文件名（CLI EXCLUDED_FILES）
     */
    private static final Set<String> EXCLUDED_FILES = Set.of(".DS_Store", ".gitignore", ".gitkeep",
            "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock");
    /**
     * 排除后缀（CLI EXCLUDED_SUFFIXES；{@code seed/content/assets/**} 豁免——install-flow D28）
     */
    private static final List<String> EXCLUDED_SUFFIXES = List.of(".map", ".zip");
    /**
     * JSON-only 构成目录（与 {@code ThemeInstallValidator.COMPOSITION_DIRS} 同一清单）
     */
    private static final Set<String> COMPOSITION_DIRS = Set.of("pages", "shapes", "seed", "i18n");
    private static final String SEED_ASSETS_PREFIX = "seed/content/assets/";
    private static final String JSON_SUFFIX = ".json";
    private static final String SEED_DIR = "seed";
    private static final String MANIFEST_FILE = "theme.manifest.json";

    /**
     * 固定 DOS 日期（2026-01-01；CLI 同值——跨运行确定性）
     */
    private static final int DOS_DATE = ((2026 - 1980) << 9) | (1 << 5) | 1;
    private static final int DOS_TIME = 0;
    /**
     * 通用位标志：UTF-8 文件名
     */
    private static final int FLAG_UTF8 = 0x0800;

    private SeedPackageWriter() {
    }

    /**
     * ZIP 条目（写入序即列表序——调用方保证确定性）
     *
     * @param name 条目名（{@code /} 分隔相对路径）
     * @param data 文件字节
     */
    public record Entry(String name, byte[] data) {
    }

    /**
     * 遍历主题目录为条目列表（CLI walk 的移植：每层名称字典序、深度优先、开发工件排除），
     * 可选以导出束替换 {@code seed/**} 与 {@code theme.manifest.json}（themeZip 模式——
     * 磁盘上的旧种子不入包；两者在遍历序中占其字典序位置，目录不存在时亦发射）。
     *
     * @param themeDir            主题目录
     * @param seedReplacement     {@code seed/} 相对路径 → 字节（{@code null} = 按磁盘现状打包）
     * @param manifestReplacement 清单字节（{@code null} = 按磁盘现状打包）
     */
    public static List<Entry> walkThemeDir(File themeDir, @Nullable SortedMap<String, byte[]> seedReplacement,
                                           @Nullable byte[] manifestReplacement) {
        List<Entry> entries = new ArrayList<>();
        TreeSet<String> names = new TreeSet<>();
        String[] children = themeDir.list();
        if (children != null) {
            for (String name : children) {
                names.add(name);
            }
        }
        if (seedReplacement != null) {
            names.add(SEED_DIR);
        }
        if (manifestReplacement != null) {
            names.add(MANIFEST_FILE);
        }
        for (String name : names) {
            if (seedReplacement != null && SEED_DIR.equals(name)) {
                seedReplacement.forEach((relpath, bytes) ->
                        entries.add(new Entry(SEED_DIR + "/" + relpath, bytes)));
                continue;
            }
            if (manifestReplacement != null && MANIFEST_FILE.equals(name)) {
                entries.add(new Entry(MANIFEST_FILE, manifestReplacement));
                continue;
            }
            walk(new File(themeDir, name), name, entries);
        }
        return entries;
    }

    private static void walk(File file, String rel, List<Entry> entries) {
        if (file.isDirectory()) {
            if (EXCLUDED_DIRS.contains(file.getName())) {
                return;
            }
            String[] children = file.list();
            if (children == null) {
                return;
            }
            TreeSet<String> names = new TreeSet<>();
            for (String name : children) {
                names.add(name);
            }
            for (String name : names) {
                walk(new File(file, name), rel + "/" + name, entries);
            }
            return;
        }
        String name = file.getName();
        if (EXCLUDED_FILES.contains(name)
                || (EXCLUDED_SUFFIXES.stream().anyMatch(name::endsWith)
                        && !rel.startsWith(SEED_ASSETS_PREFIX))) {
            return;
        }
        try {
            entries.add(new Entry(rel, Files.readAllBytes(file.toPath())));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read theme file: " + file, e);
        }
    }

    /**
     * JSON-only 预检（CLI jsonRuleViolations 的移植；服务端拒绝口径同 {@code ThemeInstallValidator}）。
     *
     * @return 违规条目名（空 = 通过）
     */
    public static List<String> compositionJsonViolations(List<Entry> entries) {
        List<String> violations = new ArrayList<>();
        for (Entry entry : entries) {
            int slash = entry.name().indexOf('/');
            if (slash < 0) {
                continue;
            }
            String topDir = entry.name().substring(0, slash);
            if (COMPOSITION_DIRS.contains(topDir) && !entry.name().startsWith(SEED_ASSETS_PREFIX)
                    && !entry.name().toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
                violations.add(entry.name());
            }
        }
        return violations;
    }

    /**
     * 写出确定性 ZIP（CLI 最小写出器的移植）：条目按列表序；每条目 deflate raw level 9，
     * 仅当压缩更小才用（否则 store）；本地头 + 中央目录 + EOCD，无 extra 字段、无注释、
     * 外部属性全零。同一条目列表恒产出同一字节序列。
     */
    public static byte[] zip(List<Entry> entries) {
        ByteArrayOutputStream locals = new ByteArrayOutputStream();
        ByteArrayOutputStream centrals = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            byte[] data = entry.data();
            byte[] deflated = deflateRaw(data);
            boolean useDeflate = deflated.length < data.length;
            byte[] payload = useDeflate ? deflated : data;
            int method = useDeflate ? 8 : 0;
            CRC32 crc32 = new CRC32();
            crc32.update(data);
            long crc = crc32.getValue();
            byte[] name = entry.name().getBytes(StandardCharsets.UTF_8);
            int offset = locals.size();

            ByteBuffer local = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
            local.putInt(0x04034b50);
            local.putShort((short) 20);
            local.putShort((short) FLAG_UTF8);
            local.putShort((short) method);
            local.putShort((short) DOS_TIME);
            local.putShort((short) DOS_DATE);
            local.putInt((int) crc);
            local.putInt(payload.length);
            local.putInt(data.length);
            local.putShort((short) name.length);
            local.putShort((short) 0);
            locals.writeBytes(local.array());
            locals.writeBytes(name);
            locals.writeBytes(payload);

            ByteBuffer central = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
            central.putInt(0x02014b50);
            central.putShort((short) 20);
            central.putShort((short) 20);
            central.putShort((short) FLAG_UTF8);
            central.putShort((short) method);
            central.putShort((short) DOS_TIME);
            central.putShort((short) DOS_DATE);
            central.putInt((int) crc);
            central.putInt(payload.length);
            central.putInt(data.length);
            central.putShort((short) name.length);
            // extra/comment/disk/internal-attrs 全零（30..37）；external attrs 零（38..41）
            central.position(42);
            central.putInt(offset);
            centrals.writeBytes(central.array());
            centrals.writeBytes(name);
        }
        ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0x06054b50);
        eocd.position(8);
        eocd.putShort((short) entries.size());
        eocd.putShort((short) entries.size());
        eocd.putInt(centrals.size());
        eocd.putInt(locals.size());

        ByteArrayOutputStream out = new ByteArrayOutputStream(locals.size() + centrals.size() + 22);
        out.writeBytes(locals.toByteArray());
        out.writeBytes(centrals.toByteArray());
        out.writeBytes(eocd.array());
        return out.toByteArray();
    }

    /**
     * deflate raw level 9（与 Node {@code deflateRawSync(data, {level: 9})} 同一 zlib 缺省参数——
     * 字节一致是 Q5 夹具测试的断言前提）。
     */
    private static byte[] deflateRaw(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, data.length / 2));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
