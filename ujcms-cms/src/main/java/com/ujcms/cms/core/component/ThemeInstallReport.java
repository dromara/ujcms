package com.ujcms.cms.core.component;

import java.util.ArrayList;
import java.util.List;

import com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult;
import org.jspecify.annotations.Nullable;

/**
 * Result of a successful pre-extraction validation run. Returned by
 * {@link ThemeInstallValidator#validate} and handed to the installer so that the post-extract
 * step does not have to re-scan the archive.
 *
 * <p>If validation fails the validator throws {@link ThemeInstallException} — this report only
 * exists for accepted ZIPs.
 */
public class ThemeInstallReport {
    private final int templateFileCount;
    private final long totalUncompressedSize;
    private final boolean hasManifest;
    @Nullable
    private final ThemeMetadata metadata;
    private final List<String> warnings;
    /**
     * 上传 ZIP 的 SHA-256（十六进制小写）。信任边界的诚实中间层（design-review P3-1 / H3）：不做签名/PKI，
     * 只把散列写进安装报告与日志，供操作员与发布方公示值带外比对。安装器计算后写入（校验器不经手临时文件）。
     */
    @Nullable
    private String sha256;
    /**
     * 随安装执行的种子导入结果（theme-seed-export §8.1 / D22：失败明细要到得了控制台）。
     * {@code seedScope=none} 或安装不带种子时为 {@code null}。安装器在导入后写入（与 sha256 同法）。
     */
    @Nullable
    private SeedRunResult seed;

    public ThemeInstallReport(int templateFileCount,
                              long totalUncompressedSize, boolean hasManifest,
                              @Nullable ThemeMetadata metadata, List<String> warnings) {
        this.templateFileCount = templateFileCount;
        this.totalUncompressedSize = totalUncompressedSize;
        this.hasManifest = hasManifest;
        this.metadata = metadata;
        this.warnings = new ArrayList<>(warnings);
    }

    public int getTemplateFileCount() { return templateFileCount; }
    public long getTotalUncompressedSize() { return totalUncompressedSize; }
    public boolean isHasManifest() { return hasManifest; }
    @Nullable public ThemeMetadata getMetadata() { return metadata; }
    public List<String> getWarnings() { return warnings; }
    @Nullable public String getSha256() { return sha256; }
    public void setSha256(@Nullable String sha256) { this.sha256 = sha256; }
    @Nullable public SeedRunResult getSeed() { return seed; }
    public void setSeed(@Nullable SeedRunResult seed) { this.seed = seed; }
}
