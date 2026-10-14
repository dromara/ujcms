package com.ujcms.cms.core.component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.support.Constants;

/**
 * Pre-extraction security and structure validator for marketplace theme ZIP uploads.
 *
 * <p>The validator never writes to disk and never extracts the archive — it only inspects
 * entry metadata and the content of {@code theme.manifest.json}. The {@link ThemeInstallService}
 * runs validation first and aborts the install before any file is written if any rule fails.
 *
 * <h2>Rules enforced</h2>
 * <ol>
 *   <li><b>Size limit</b> — total uncompressed size must not exceed the configured maximum
 *       (default {@link #DEFAULT_MAX_SIZE_MB} MB; see {@link com.ujcms.cms.core.web.backendapi.ThemeController}).</li>
 *   <li><b>Zip-slip</b> — entries must not contain {@code ..}, absolute paths, or backslashes.</li>
 *   <li><b>Reserved paths</b> — entries must not match Windows reserved device names.</li>
 *   <li><b>No nested theme directory</b> — every entry must not share a single common top-level
 *       directory prefix (e.g., zipping {@code acme-blog/} as the archive root is rejected;
 *       operators must zip the contents).</li>
 *   <li><b>No Liquid</b> — the Liquid engine was retired; any {@code .liquid} file outside the
 *       asset dirs ({@link #NON_TEMPLATE_DIRS}) and {@code seed/} is rejected with
 *       {@code theme.install.error.liquidNotSupported}. Applies to renderer themes and data-only
 *       packages alike.</li>
 *   <li><b>Required template</b> — at least one {@code .html} template file must exist under
 *       {@code templates/} (the structured layout is the single convention; root-level
 *       {@code .html} files do not count).</li>
 *   <li><b>Optional {@code theme.manifest.json}</b> — if present, must parse as JSON and
 *       {@code kind} (if set) must be {@code theme} or {@code data-only}.</li>
 *   <li><b>Data-only packages</b> ({@code kind: "data-only"}, §8) — the required-template rule is
 *       replaced by its inversion: the package must NOT contain any renderer template
 *       ({@code .html} outside {@code seed/} and the asset dirs) — seeds without renderers is the
 *       definition of the kind. All security and JSON-only rules (zip-slip, size caps, composition
 *       dirs incl. the {@code seed/content/assets/**} carve-out) apply unchanged.</li>
 * </ol>
 *
 * <p>This class is stateless; the {@link ObjectMapper} is shared with the rest of the app.
 */
@Component
public class ThemeInstallValidator {
    /**
     * Default of {@code ujcms.theme.install.maxSizeMb}: the uncompressed size budget, in MB, shared by
     * the install side, the seed exporter and the build-time bundled-theme packager.
     */
    public static final int DEFAULT_MAX_SIZE_MB = 50;

    /** Converts a {@code ujcms.theme.install.maxSizeMb} value to the byte budget the size rule compares against. */
    public static long megabytesToBytes(int megabytes) {
        return megabytes * 1024L * 1024L;
    }
    /** Default soft cap on individual {@code theme.manifest.json} size to avoid pathological JSON. */
    private static final int MAX_THEME_MANIFEST_BYTES = 64 * 1024;
    /** Hard cap on entry count, mirroring {@link com.ujcms.common.file.ZipUtils#MAX_ENTRIES} ratio. */
    private static final int MAX_ENTRIES = 10_000;
    /** Per-entry max size to short-circuit zip-bomb attempts inside a "small" archive. */
    private static final long MAX_ENTRY_SIZE = 100L * 1024 * 1024;
    /** Hard cap on filename length. */
    private static final int MAX_PATH_LENGTH = 240;
    /** Retired Liquid template suffix — only referenced here to reject third-party Liquid ZIPs. */
    private static final String LIQUID_SUFFIX = ".liquid";
    /** Legacy asset dir name kept for third-party ZIPs (shipped themes use {@code assets/}). */
    private static final String LEGACY_FILES_DIR = "_files";
    /** Files outside these dirs count as template files for the structure rules. */
    private static final Set<String> NON_TEMPLATE_DIRS = Set.of(
            LEGACY_FILES_DIR, Constants.TEMPLATE_ASSETS, "_src", "node_modules");
    /**
     * Composition data dirs that must contain ONLY {@code .json} files (§12.1 JSON-only rule).
     * This keeps stray renderer files out of {@code pages/}/{@code shapes/}/{@code seed/}/{@code i18n/}
     * so registry scanning never sees a renderer template there.
     */
    private static final Set<String> COMPOSITION_DIRS = Set.of("pages", "shapes", "seed", "i18n");
    private static final String JSON_SUFFIX = ".json";
    /**
     * 内容种子媒体目录（theme-marketplace §5.5）：JSON-only 规则的唯一例外
     */
    private static final String SEED_CONTENT_ASSETS_DIR = "seed/content/assets/";
    /** Windows reserved device names; case-insensitive, base name only. */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final Pattern PATH_SEGMENT = Pattern.compile("[/]+");

    private final ObjectMapper objectMapper;

    public ThemeInstallValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validate the supplied ZIP. The stream is read once; the caller is responsible for
     * supplying a fresh stream (e.g. {@link org.springframework.web.multipart.MultipartFile#getInputStream()}).
     *
     * @param zipInput a fresh InputStream over the uploaded ZIP
     * @param uploadedSize the {@code Content-Length}-reported size; used for the size check
     * @param maxSizeBytes the configured maximum allowed (e.g. 50 MB)
     * @return a {@link ThemeInstallReport} on success
     * @throws ThemeInstallException if any rule fails
     */
    public ThemeInstallReport validate(InputStream zipInput, long uploadedSize, long maxSizeBytes) {
        if (uploadedSize > maxSizeBytes) {
            throw new ThemeInstallException("theme.install.error.tooLarge",
                    "Theme ZIP exceeds maximum size of " + (maxSizeBytes / (1024 * 1024)) + " MB.");
        }

        List<EntryRecord> entries = new ArrayList<>();
        byte[] manifestBytes = null;
        long totalUncompressed = 0;

        try (ZipInputStream zis = new ZipInputStream(zipInput, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entries.size() >= MAX_ENTRIES) {
                    throw new ThemeInstallException("theme.install.error.tooManyEntries",
                            "Theme ZIP contains more than " + MAX_ENTRIES + " entries.");
                }
                String rawName = entry.getName();
                String normalized = validatePath(rawName);
                EntryRecord rec = new EntryRecord(normalized, entry.isDirectory());
                entries.add(rec);

                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                long size = readEntry(zis, rec, totalUncompressed, maxSizeBytes,
                                      "theme.manifest.json".equals(rec.basename()));
                totalUncompressed += size;
                if (totalUncompressed > maxSizeBytes) {
                    throw new ThemeInstallException("theme.install.error.tooLarge",
                            "Uncompressed theme size exceeds " + (maxSizeBytes / (1024 * 1024)) + " MB.");
                }
                if (rec.capturedBytes != null) {
                    if ("theme.manifest.json".equals(rec.normalized) && manifestBytes == null) {
                        manifestBytes = rec.capturedBytes;
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.malformedZip",
                    "Theme ZIP could not be read: " + e.getMessage(), e);
        }

        if (entries.isEmpty()) {
            throw new ThemeInstallException("theme.install.error.empty",
                    "Theme ZIP is empty.");
        }

        checkCompositionDirsJsonOnly(entries);
        // Liquid 引擎已退役：两类包（渲染器主题 / 纯数据包）一律拒绝 .liquid，先于 data-only 分支
        checkNoLiquid(entries);

        // 先解析清单再做结构检查：kind 决定走哪套结构规则（§8 data-only）
        ThemeMetadata metadata = manifestBytes != null ? parseMetadata(manifestBytes) : null;
        validateKind(metadata);
        List<String> warnings = new ArrayList<>();

        if (metadata != null && metadata.isDataOnly()) {
            checkDataOnlyStructure(entries, warnings);
            // 纯数据包无渲染器；templateCount=0 是事实，消费端按 kind 分支
            return new ThemeInstallReport(0, totalUncompressed, true, metadata, warnings);
        }

        int templateCount = (int) entries.stream().filter(EntryRecord::isTemplateFile).count();
        if (templateCount == 0) {
            // Either the archive is wrapped in an extra folder (acme-blog/templates/index.html)
            // or the theme is genuinely missing page templates. Either way, the operator
            // must re-zip with page templates under templates/ at the archive root.
            throw new ThemeInstallException("theme.install.error.missingTemplate",
                    "Theme ZIP must contain at least one .html template file under templates/. "
                            + "If you zipped a folder, re-zip the folder's contents instead.");
        }

        return new ThemeInstallReport(templateCount, totalUncompressed,
                manifestBytes != null, metadata, warnings);
    }

    /**
     * {@code kind} 取值校验（安装门槛从严：未知值拒绝而非忽略——已装目录的读取路径仍宽容，
     * {@link ThemeMetadata#isDataOnly()} 只认 {@code data-only}）。
     */
    private void validateKind(@Nullable ThemeMetadata metadata) {
        String kind = metadata != null ? metadata.getKind() : null;
        if (kind != null && !ThemeMetadata.KIND_THEME.equals(kind)
                && !ThemeMetadata.KIND_DATA_ONLY.equals(kind)) {
            throw new ThemeInstallException("theme.install.error.unknownKind",
                    "theme.manifest.json declares unknown kind '" + kind
                            + "'. Supported: theme, data-only.");
        }
    }

    /**
     * Liquid 退役门槛：资产目录（{@link #NON_TEMPLATE_DIRS}）与 {@code seed/} 之外出现
     * {@code .liquid} 即拒绝——不支持 Liquid 主题，请改用 FreeMarker（{@code .html}）。
     * 渲染器主题与纯数据包均适用（先于 data-only 分支执行）。
     */
    private void checkNoLiquid(List<EntryRecord> entries) {
        for (EntryRecord rec : entries) {
            if (rec.isDirectory || rec.isInsideNonTemplateDir()) {
                continue;
            }
            int slash = rec.normalized.indexOf('/');
            String first = slash < 0 ? null : rec.normalized.substring(0, slash);
            if ("seed".equals(first)) {
                continue;
            }
            if (rec.normalized.endsWith(LIQUID_SUFFIX)) {
                throw new ThemeInstallException("theme.install.error.liquidNotSupported",
                        "Liquid themes (.liquid) are not supported; use FreeMarker (.html) "
                                + "templates instead. Offending entry: " + rec.normalized);
            }
        }
    }

    /**
     * 纯数据包结构规则（§8）：不得携带任何渲染器模板——{@code seed/} 与资产目录
     * （{@link #NON_TEMPLATE_DIRS}）之外出现 {@code .html} 即拒绝。
     * 「无渲染器的种子」是该包类型的定义；带渲染器却永不可激活的包只能是作者笔误，装门槛拦下。
     * 随包 {@code pages/}/{@code shapes/} 合法（JSON-only 规则管辖）但仅对生效主题起作用，
     * 纯数据包永不激活——告警提示。
     */
    private void checkDataOnlyStructure(List<EntryRecord> entries, List<String> warnings) {
        boolean hasPagesOrShapes = false;
        for (EntryRecord rec : entries) {
            if (rec.isDirectory) {
                continue;
            }
            int slash = rec.normalized.indexOf('/');
            String first = slash < 0 ? null : rec.normalized.substring(0, slash);
            if ("pages".equals(first) || "shapes".equals(first)) {
                hasPagesOrShapes = true;
            }
            if (rec.isInsideNonTemplateDir() || "seed".equals(first)) {
                continue;
            }
            if (rec.normalized.endsWith(Constants.TEMPLATE_SUFFIX)) {
                throw new ThemeInstallException("theme.install.error.dataOnlyHasTemplates",
                        "A data-only package (kind: \"data-only\") must not contain renderer "
                                + "templates. Offending entry: " + rec.normalized);
            }
        }
        if (hasPagesOrShapes) {
            warnings.add("data-only package ships pages/ or shapes/ — they only take effect for "
                    + "an active theme, and a data-only package can never be activated");
        }
    }

    private long readEntry(ZipInputStream zis, EntryRecord rec, long runningTotal,
                           long maxSizeBytes, boolean capture) throws IOException {
        ByteArrayOutputStream captureBuf = capture ? new ByteArrayOutputStream() : null;
        byte[] buf = new byte[8192];
        long size = 0;
        int n;
        while ((n = zis.read(buf)) != -1) {
            size += n;
            if (size > MAX_ENTRY_SIZE) {
                throw new ThemeInstallException("theme.install.error.entryTooLarge",
                        "Entry '" + rec.normalized + "' exceeds " + (MAX_ENTRY_SIZE / (1024 * 1024)) + " MB.");
            }
            if (runningTotal + size > maxSizeBytes) {
                throw new ThemeInstallException("theme.install.error.tooLarge",
                        "Uncompressed theme size exceeds " + (maxSizeBytes / (1024 * 1024)) + " MB.");
            }
            if (captureBuf != null) {
                if (captureBuf.size() + n > MAX_THEME_MANIFEST_BYTES) {
                    throw new ThemeInstallException("theme.install.error.metadataTooLarge",
                            "theme.manifest.json exceeds " + MAX_THEME_MANIFEST_BYTES + " bytes.");
                }
                captureBuf.write(buf, 0, n);
            }
        }
        if (captureBuf != null) {
            rec.capturedBytes = captureBuf.toByteArray();
        }
        return size;
    }

    private String validatePath(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            throw new ThemeInstallException("theme.install.error.invalidEntryName",
                    "Entry has empty name.");
        }
        if (rawName.length() > MAX_PATH_LENGTH) {
            throw new ThemeInstallException("theme.install.error.invalidEntryName",
                    "Entry path too long: " + rawName);
        }
        if (rawName.indexOf('\\') >= 0) {
            throw new ThemeInstallException("theme.install.error.invalidEntryName",
                    "Entry name uses backslash separators: " + rawName);
        }
        if (rawName.startsWith("/")) {
            throw new ThemeInstallException("theme.install.error.absolutePath",
                    "Entry name is absolute: " + rawName);
        }
        // Reject Windows-style absolute paths like C:\... or C:/...
        if (rawName.length() > 2 && rawName.charAt(1) == ':') {
            throw new ThemeInstallException("theme.install.error.absolutePath",
                    "Entry name looks like a drive-letter path: " + rawName);
        }
        for (String segment : PATH_SEGMENT.split(rawName)) {
            if (segment.isEmpty()) continue;
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new ThemeInstallException("theme.install.error.zipSlip",
                        "Entry name escapes archive root: " + rawName);
            }
            int dot = segment.indexOf('.');
            String base = dot < 0 ? segment : segment.substring(0, dot);
            if (RESERVED_NAMES.contains(base.toUpperCase())) {
                throw new ThemeInstallException("theme.install.error.reservedName",
                        "Entry uses Windows reserved name: " + rawName);
            }
        }
        return rawName;
    }

    /**
     * JSON-only rule for composition data dirs (§12.1). Files directly or transitively under
     * {@code pages/}, {@code shapes/}, {@code seed/}, {@code i18n/} (at the theme root) must end
     * with {@code .json}. Keeps stray renderer files out of the composition dirs.
     * 例外：{@code seed/content/assets/}（theme-marketplace §5.5）——内容种子随包媒体文件，
     * 导入时复制进上传存储，天然非 JSON。
     */
    private void checkCompositionDirsJsonOnly(List<EntryRecord> entries) {
        for (EntryRecord rec : entries) {
            if (rec.isDirectory) {
                continue;
            }
            int slash = rec.normalized.indexOf('/');
            if (slash < 0) {
                continue;
            }
            String first = rec.normalized.substring(0, slash);
            if (rec.normalized.startsWith(SEED_CONTENT_ASSETS_DIR)) {
                continue;
            }
            if (COMPOSITION_DIRS.contains(first)
                    && !rec.normalized.toLowerCase(java.util.Locale.ROOT).endsWith(JSON_SUFFIX)) {
                throw new ThemeInstallException("theme.install.error.compositionNonJson",
                        "Composition directory '" + first + "/' may contain only .json files. "
                                + "Offending entry: " + rec.normalized);
            }
        }
    }

    private ThemeMetadata parseMetadata(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, ThemeMetadata.class);
        } catch (JsonProcessingException e) {
            throw new ThemeInstallException("theme.install.error.metadataMalformed",
                    "theme.manifest.json is not valid JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.metadataMalformed",
                    "theme.manifest.json could not be read: " + e.getMessage(), e);
        }
    }

    /** Raised entries kept around for cross-entry checks (structure, nesting, count). */
    private static final class EntryRecord {
        final String normalized;
        final boolean isDirectory;
        @Nullable byte[] capturedBytes;

        EntryRecord(String normalized, boolean isDirectory) {
            this.normalized = normalized;
            this.isDirectory = isDirectory;
        }

        @Nullable
        String basename() {
            int slash = normalized.lastIndexOf('/');
            String b = slash < 0 ? normalized : normalized.substring(slash + 1);
            return b.isEmpty() ? null : b;
        }

        boolean isInsideNonTemplateDir() {
            int slash = normalized.indexOf('/');
            if (slash < 0) return false;
            String first = normalized.substring(0, slash);
            return NON_TEMPLATE_DIRS.contains(first);
        }

        /**
         * A file qualifies as a renderable theme template if it has the {@code .html} extension
         * AND sits under {@code templates/} (the structured layout is the single convention;
         * root-level {@code .html} does not count). This rule doubles as the "wrapped folder"
         * check — a ZIP with {@code acme-blog/templates/index.html} has zero qualifying
         * templates because nothing is under {@code templates/} at the archive root.
         */
        boolean isTemplateFile() {
            if (isDirectory) return false;
            if (isInsideNonTemplateDir()) return false;
            String b = basename();
            if (b == null || !b.endsWith(Constants.TEMPLATE_SUFFIX)) return false;
            int slash = normalized.indexOf('/');
            if (slash < 0) return false;
            String firstDir = normalized.substring(0, slash);
            return "templates".equals(firstDir);
        }
    }

    /** Exposed for tests that want to introspect the rule keys. */
    static Set<String> nonTemplateDirs() {
        return new LinkedHashSet<>(NON_TEMPLATE_DIRS);
    }
}
