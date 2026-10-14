package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性 ZIP 写出器测试（theme-seed-export E2 §7.1）。核心断言（Q5）：与
 * {@code scripts/build-seed-package.mjs} <b>逐项字节一致</b>——
 * {@code composition/zip-parity/expected.zip} 为 CLI 打包同一夹具目录的检入产物；
 * 条目序列、每条目元数据（方法/CRC/大小/固定时间戳）与解压内容必须完全一致。
 * <b>deflate 位流本身除外</b>：Node 附带 Chromium 的 zlib 分叉（{@code 1.3.1-470d3a2}），
 * 同参数下匹配选择与原版 zlib（JDK）不同——跨工具的位流一致不是可守护的契约
 * （Node 升级即漂移）；两工具各自的输出仍字节确定（D4，见下）。Q5 决议记录于计划 §11。
 */
class SeedPackageWriterTest {

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                SeedPackageWriterTest.class.getResource("/composition/zip-parity/" + name)).toURI());
    }

    @Test
    void parityWithCliPacker(@TempDir Path tmp) throws Exception {
        List<SeedPackageWriter.Entry> entries =
                SeedPackageWriter.walkThemeDir(resource("theme").toFile(), null, null);
        byte[] mine = SeedPackageWriter.zip(entries);
        byte[] cli = Files.readAllBytes(resource("expected.zip"));
        List<EntryFacts> mineFacts = entryFacts(tmp.resolve("mine.zip"), mine);
        List<EntryFacts> cliFacts = entryFacts(tmp.resolve("cli.zip"), cli);
        assertEquals(cliFacts, mineFacts,
                "服务端 ZIP 必须与 CLI 打包器逐项一致（Q5）：条目序/方法/CRC/大小/时间戳/内容散列——"
                        + "遍历序、排除清单、时间戳或 store-vs-deflate 分派任一漂移都在此失败");
    }

    /**
     * 条目事实（deflate 位流除外的全部可比字段；内容以 CRC + 解压字节比对）
     */
    private record EntryFacts(String name, int method, long crc, long size, LocalDateTime time,
                              String contentSha) {
    }

    private static List<EntryFacts> entryFacts(Path file, byte[] zip) throws IOException {
        Files.write(file, zip);
        List<EntryFacts> facts = new ArrayList<>();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(file.toFile(),
                StandardCharsets.UTF_8)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                byte[] content;
                try (var in = zf.getInputStream(entry)) {
                    content = in.readAllBytes();
                }
                String contentSha;
                try {
                    contentSha = java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(content));
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
                facts.add(new EntryFacts(entry.getName(), entry.getMethod(), entry.getCrc(),
                        entry.getSize(), entry.getTimeLocal(), contentSha));
            }
        }
        return facts;
    }

    @Test
    void walkAppliesCliExclusionsAndOrder() throws Exception {
        List<SeedPackageWriter.Entry> entries =
                SeedPackageWriter.walkThemeDir(resource("theme").toFile(), null, null);
        List<String> names = entries.stream().map(SeedPackageWriter.Entry::name).toList();
        assertEquals(List.of(
                "assets/css/site.css",
                "i18n/zh-CN.json",
                "seed/content/assets/img/logo.svg",
                "seed/content/assets/img/rand.bin",
                "seed/content/assets/media/sample.zip",
                "seed/regions/home.regions.json",
                "templates/index.html",
                "theme.manifest.json"), names,
                "每层名称字典序的深度优先 + 开发工件排除（_src/.map/package.json/根部杂散 .zip）；"
                        + "后缀排除豁免 seed/content/assets/**（内容媒体可携 .zip 附件——install-flow D28）");
    }

    @Test
    void zipIsDeterministicAndJavaReadable() throws Exception {
        List<SeedPackageWriter.Entry> entries =
                SeedPackageWriter.walkThemeDir(resource("theme").toFile(), null, null);
        byte[] first = SeedPackageWriter.zip(entries);
        assertArrayEquals(first, SeedPackageWriter.zip(entries), "同条目列表重打包必须字节一致（D4）");

        // java.util.zip 可读（安装校验器同一解析器）；store/deflate 按「压缩更小才用」分派；
        // 固定时间戳 2026-01-01 00:00
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(first),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int index = 0;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
                assertArrayEquals(entries.get(index).data(), in.readAllBytes(),
                        "条目内容必须往返一致: " + entry.getName());
                assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), entry.getTimeLocal(),
                        "固定 DOS 时间戳（确定性）: " + entry.getName());
                if ("seed/content/assets/img/rand.bin".equals(entry.getName())) {
                    assertEquals(ZipEntry.STORED, entry.getMethod(), "不可压缩条目应为 store");
                }
                if ("templates/index.html".equals(entry.getName())) {
                    assertEquals(ZipEntry.DEFLATED, entry.getMethod(), "可压缩条目应为 deflate");
                }
                in.closeEntry();
                index++;
            }
        }
        assertEquals(entries.stream().map(SeedPackageWriter.Entry::name).toList(), names);
    }

    @Test
    void walkReplacesSeedAndManifestAtTheirSortedPositions(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("a.txt"), "a");
        Files.createDirectories(tmp.resolve("seed/content"));
        Files.writeString(tmp.resolve("seed/content/stale.articles.json"), "{}");
        Files.writeString(tmp.resolve("theme.manifest.json"), "{ \"version\": \"0.1.0\" }");
        Files.writeString(tmp.resolve("zoo.txt"), "z");

        TreeMap<String, byte[]> seed = new TreeMap<>(Map.of(
                "content/channels.channels.json", "{\"channels\":[]}".getBytes(StandardCharsets.UTF_8),
                "regions/home.regions.json", "{}".getBytes(StandardCharsets.UTF_8)));
        byte[] manifest = "{ \"version\": \"1.0.0\" }".getBytes(StandardCharsets.UTF_8);
        List<SeedPackageWriter.Entry> entries = SeedPackageWriter.walkThemeDir(tmp.toFile(), seed, manifest);
        assertEquals(List.of(
                "a.txt",
                "seed/content/channels.channels.json",
                "seed/regions/home.regions.json",
                "theme.manifest.json",
                "zoo.txt"), entries.stream().map(SeedPackageWriter.Entry::name).toList(),
                "seed/** 与清单以替换内容占据其字典序位置；磁盘旧种子不入包");
        assertArrayEquals(manifest, entries.get(3).data());
    }

    @Test
    void walkEmitsReplacementsEvenWhenAbsentOnDisk(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("templates"));
        Files.writeString(tmp.resolve("templates/index.html"), "<html/>");
        TreeMap<String, byte[]> seed = new TreeMap<>(Map.of(
                "regions/home.regions.json", "{}".getBytes(StandardCharsets.UTF_8)));
        byte[] manifest = "{}".getBytes(StandardCharsets.UTF_8);
        List<SeedPackageWriter.Entry> entries = SeedPackageWriter.walkThemeDir(tmp.toFile(), seed, manifest);
        assertEquals(List.of("seed/regions/home.regions.json", "templates/index.html",
                "theme.manifest.json"), entries.stream().map(SeedPackageWriter.Entry::name).toList());
    }

    @Test
    void compositionJsonOnlyRuleWithAssetsCarveOut() {
        List<SeedPackageWriter.Entry> entries = List.of(
                new SeedPackageWriter.Entry("seed/content/assets/img/a.png", new byte[]{1}),
                new SeedPackageWriter.Entry("seed/evil.html", new byte[]{1}),
                new SeedPackageWriter.Entry("pages/home.page.txt", new byte[]{1}),
                new SeedPackageWriter.Entry("templates/index.html", new byte[]{1}),
                new SeedPackageWriter.Entry("i18n/zh-CN.json", new byte[]{1}));
        assertEquals(List.of("seed/evil.html", "pages/home.page.txt"),
                SeedPackageWriter.compositionJsonViolations(entries),
                "构成目录 JSON-only；唯一例外 seed/content/assets/**（与安装校验器同规则）");
    }

    @Test
    void utf8EntryNamesSurviveRoundTrip() throws IOException {
        List<SeedPackageWriter.Entry> entries = List.of(
                new SeedPackageWriter.Entry("seed/content/assets/img/图标.svg",
                        "<svg/>".getBytes(StandardCharsets.UTF_8)));
        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(SeedPackageWriter.zip(entries)), StandardCharsets.UTF_8)) {
            ZipEntry entry = in.getNextEntry();
            assertTrue(entry != null && "seed/content/assets/img/图标.svg".equals(entry.getName()));
        }
    }
}
