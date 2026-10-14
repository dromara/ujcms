package com.ujcms.cms.core.composition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.ujcms.cms.core.component.ThemeInstallValidator;

/**
 * 构建期内置主题打包入口（install-flow §6 P3 / D6 / Q2）：Maven 构建时把 git 授权目录
 * （{@code ujcms-starter/src/main/webapp/themes/1/default}）经 {@link SeedPackageWriter}
 * 的确定性规则打成类路径资源 {@code bundled-themes/<name>.zip}——纯 Java、无 Node 依赖
 * （Q2 决议）；排除规则、条目序、固定时间戳与 CLI 打包器逐项一致（theme-seed-export D13，
 * deflate 位流豁免）。{@code SystemCoreBootstrap} 于首装引导把产物注册进主题包库。
 *
 * <p>由 {@code ujcms-starter/pom.xml} 的 {@code exec-maven-plugin}（{@code exec:java}，
 * {@code generate-resources} 阶段）调用——运行在 Maven 进程内，失败以异常上抛使构建失败，
 * <b>绝不</b> {@code System.exit}。
 *
 * <p>用法：{@code BundledThemePackager <themeDir> <outFile> [maxSizeMb]}
 *
 * @author PONY
 */
public final class BundledThemePackager {

    private BundledThemePackager() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: BundledThemePackager <themeDir> <outFile> [maxSizeMb]");
        }
        File themeDir = new File(args[0]);
        Path outFile = Path.of(args[1]);
        int maxSizeMb = args.length > 2
                ? Integer.parseInt(args[2]) : ThemeInstallValidator.DEFAULT_MAX_SIZE_MB;
        long maxBytes = ThemeInstallValidator.megabytesToBytes(maxSizeMb);

        if (!themeDir.isDirectory()) {
            throw new IllegalArgumentException("Theme dir not found: " + themeDir);
        }
        if (!new File(themeDir, "theme.manifest.json").isFile()) {
            throw new IllegalStateException("Theme dir has no theme.manifest.json (the library "
                    + "registration needs manifest themeName/version): " + themeDir);
        }
        List<SeedPackageWriter.Entry> entries = SeedPackageWriter.walkThemeDir(themeDir, null, null);
        List<String> violations = SeedPackageWriter.compositionJsonViolations(entries);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Non-JSON files in composition dirs (only "
                    + "seed/content/assets/** may carry media): " + String.join(", ", violations));
        }
        byte[] zip = SeedPackageWriter.zip(entries);
        long uncompressed = entries.stream().mapToLong(entry -> entry.data().length).sum();
        // 安装上限预检（§7.3 同款口径）：超限的内置包装不上，构建期就失败
        if (uncompressed > maxBytes || zip.length > maxBytes) {
            throw new IllegalStateException("Bundled theme package exceeds the install cap: uncompressed "
                    + uncompressed + " bytes, zip " + zip.length + " bytes, cap " + maxBytes + " bytes ("
                    + maxSizeMb + " MB). Check the packer exclusion rules or ujcms.theme.install.maxSizeMb.");
        }
        Files.createDirectories(outFile.getParent());
        Files.write(outFile, zip);
        System.out.println("Bundled theme package: " + outFile + " (" + entries.size() + " entries, "
                + zip.length + " bytes, uncompressed " + uncompressed + " bytes)");
    }
}
