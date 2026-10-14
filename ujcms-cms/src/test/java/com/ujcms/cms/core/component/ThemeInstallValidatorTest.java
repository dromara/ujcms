package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ThemeInstallValidator} fail-fast rule set. Each rule has at least one
 * happy-path and one failing-path test.
 */
class ThemeInstallValidatorTest {
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private final ThemeInstallValidator validator = new ThemeInstallValidator(new ObjectMapper());

    private byte[] zipOf(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                if (e.getValue() != null) {
                    zos.write(e.getValue());
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private InputStream stream(byte[] zip) {
        return new ByteArrayInputStream(zip);
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ----- happy paths ----------------------------------------------------

    @Test
    void structuredThemeAccepted() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("<html></html>"));
        entries.put("templates/article.html", bytes("<html></html>"));
        entries.put("snippets/header.html", bytes("hdr"));
        entries.put("layout/theme.html", bytes("layout"));
        entries.put("assets/css/style.css", bytes(".x{}"));
        entries.put("theme.manifest.json",
                bytes("{\"name\":\"Acme\",\"themeName\":\"acme\",\"version\":\"1.0.0\","
                        + "\"templateSpecVersion\":\"2.0\"}"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        // Page templates only — snippets/ and layout/ are partials, not picker-visible pages.
        assertEquals(2, report.getTemplateFileCount());
        assertNotNull(report.getMetadata());
        assertEquals("Acme", report.getMetadata().getName());
        assertEquals("1.0.0", report.getMetadata().getVersion());
    }

    @Test
    void themeWithOnlyTemplatesDirAccepted() throws IOException {
        // Single top-level dir 'templates/' is the canonical structured-layout entry, so a
        // theme that ships only page templates (no snippets/layout/assets) installs fine.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("<html></html>"));
        byte[] zip = zipOf(entries);
        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
        assertNull(report.getMetadata());
    }

    @Test
    void htmlInsideAssetsDirDoesNotCountAsTemplate() throws IOException {
        // assets/ contents are excluded from the template rules (per spec).
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("<html></html>"));
        entries.put("assets/legacy.html", bytes("<p>html inside assets/</p>"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    // ----- Liquid retirement -----------------------------------------------

    @Test
    void liquidTemplateRejected() throws IOException {
        // The Liquid engine was retired — any .liquid outside the asset dirs is rejected
        // outright, even when .html templates are also present.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("html"));
        entries.put("templates/article.liquid", bytes("liquid"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.liquidNotSupported", ex.getMessage());
    }

    @Test
    void pureLiquidThemeRejectedAsLiquidNotSupported() throws IOException {
        // A pure-Liquid ZIP gets the explicit "not supported" error, not missingTemplate —
        // the operator learns the real reason instead of a misleading structure hint.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.liquid", bytes("liquid"));
        entries.put("templates/article.liquid", bytes("liquid"));
        entries.put("snippets/inc_header.liquid", bytes("hdr"));
        entries.put("layout/theme.liquid", bytes("layout"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.liquidNotSupported", ex.getMessage());
    }

    @Test
    void liquidInsideAssetsDirStillAccepted() throws IOException {
        // Asset dirs may carry .liquid content files (e.g. vendored docs); only renderer
        // locations are policed.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("html"));
        entries.put("assets/vendor/example.liquid", bytes("not a renderer"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    @Test
    void dataOnlyWithLiquidRejectedAsLiquidNotSupported() throws IOException {
        // The Liquid gate runs before the data-only branch: a data-only package smuggling a
        // .liquid renderer gets liquidNotSupported, not dataOnlyHasTemplates.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("index.liquid", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.liquidNotSupported", ex.getMessage());
    }

    // ----- size limits ----------------------------------------------------

    @Test
    void uploadedSizeOverLimitRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), MAX_BYTES + 1, MAX_BYTES));
        assertEquals("theme.install.error.tooLarge", ex.getMessage());
    }

    @Test
    void uncompressedSizeOverLimitRejected() throws IOException {
        // Build an entry whose decompressed size exceeds a small max.
        long smallMax = 1024;
        byte[] bigPayload = new byte[(int) smallMax + 100];
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bigPayload);
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, smallMax));
        assertEquals("theme.install.error.tooLarge", ex.getMessage());
    }

    // ----- zip-slip / path checks -----------------------------------------

    @Test
    void zipSlipParentPathRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../escape.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.zipSlip", ex.getMessage());
    }

    @Test
    void absolutePathRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("/etc/passwd", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.absolutePath", ex.getMessage());
    }

    @Test
    void backslashPathRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("foo\\bar.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.invalidEntryName", ex.getMessage());
    }

    @Test
    void windowsReservedNameRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("CON.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.reservedName", ex.getMessage());
    }

    // ----- structure checks ----------------------------------------------

    @Test
    void emptyZipRejected() throws IOException {
        byte[] zip = zipOf(new LinkedHashMap<>());
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.empty", ex.getMessage());
    }

    @Test
    void noTemplateFileRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("README.md", bytes("readme"));
        entries.put("assets/style.css", bytes(".x{}"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    @Test
    void rootLevelHtmlOnlyRejectedAsMissingTemplate() throws IOException {
        // The flat layout was retired with the Liquid engine — page templates must live under
        // templates/; root-level .html files no longer count.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("index.html", bytes("hi"));
        entries.put("channel.html", bytes("c"));
        entries.put("_files/css/style.css", bytes("body{}"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    @Test
    void wrappedThemeDirectoryRejectedAsMissingTemplate() throws IOException {
        // Operator zipped a wrapper folder rather than the theme contents — every entry sits
        // under "acme-blog/", so no template is under templates/ at the archive root. Caught by
        // the missingTemplate rule rather than a separate heuristic.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("acme-blog/templates/index.html", bytes("html"));
        entries.put("acme-blog/templates/article.html", bytes("html"));
        entries.put("acme-blog/snippets/header.html", bytes("hdr"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    @Test
    void themeWithOnlySnippetsRejected() throws IOException {
        // Partials only, no actual page templates under templates/ → reject.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("snippets/header.html", bytes("hdr"));
        byte[] zip = zipOf(entries);
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    // ----- theme.manifest.json --------------------------------------------------

    @Test
    void malformedTemplateJsonRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("theme.manifest.json", bytes("{ this is not json"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.metadataMalformed", ex.getMessage());
    }

    @Test
    void unknownTemplateJsonFieldsAreIgnored() throws IOException {
        // Includes the retired `engine` field — stale manifests still deserialize (ignoreUnknown).
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("theme.manifest.json",
                bytes("{\"name\":\"x\",\"version\":\"1\",\"engine\":\"freemarker\","
                        + "\"futureField\":\"hello\"}"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals("x", report.getMetadata().getName());
    }

    // ----- structure rules: subdirs handled correctly ----------------------

    @Test
    void htmlInsideFilesDirDoesNotCountAsTemplate() throws IOException {
        // _files/ is the legacy asset dir; .html inside is content, not a template.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("html"));
        entries.put("_files/legacy/snippet.html", bytes("<p>not a template</p>"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    @Test
    void htmlInsideNodeModulesIgnored() throws IOException {
        // node_modules/ may be present in dev artifacts; its .html files are noise, not pages.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("html"));
        entries.put("node_modules/foo/template.html", bytes("noise"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    // ----- safety -----------------------------------------------------------

    @Test
    void totalSizeIncludesAllEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", new byte[256]);
        entries.put("templates/article.html", new byte[256]);
        byte[] zip = zipOf(entries);
        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertTrue(report.getTotalUncompressedSize() >= 512);
    }

    // ----- Phase 6 security audit fuzz probes -----------------------------

    @Test
    void zipSlipDeeplyNestedRejected() throws IOException {
        // Confirms that a `..` segment buried mid-path is still caught — not only segments at the
        // archive root. Lock-in for the deeply nested traversal class.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/foo/bar/../../../../etc/passwd", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.zipSlip", ex.getMessage());
    }

    @Test
    void singleDotSegmentRejected() throws IOException {
        // `.` segments don't escape the archive but also serve no purpose in a legitimate theme;
        // rejecting them removes a normalization-divergence risk between the validator's path
        // checks and the extractor's `Path.normalize()` (which collapses `.` segments silently).
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/./index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.zipSlip", ex.getMessage());
    }

    @Test
    void urlEncodedTraversalIsTreatedAsLiteralPathSegment() throws IOException {
        // The validator splits on '/' only. A `%2e%2e` sequence is a literal segment, not a
        // decoded `..`, so it's accepted as a strange-but-non-escaping name. Lock the behaviour
        // so a future "decode URLs in entry names" refactor can't silently reopen zip-slip via
        // double-encoded payloads. The entry has no real templates so the structure check trips.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("%2e%2e/%2e%2e/etc/passwd", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        // missingTemplate (no .html under templates/) — NOT zipSlip, because %2e%2e is a literal
        // directory name, not a decoded `..`. The point of this test is to detect a regression
        // where the validator starts URL-decoding (which would change the assertion to zipSlip
        // and would be a vulnerability, since the extractor doesn't URL-decode either).
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    @Test
    void caseInsensitiveReservedNameRejected() throws IOException {
        // RESERVED_NAMES is upper-case but the check uppercases the basename, so lowercase
        // collisions like 'con.html' or mixed-case 'Aux.html' should be rejected too.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/aux.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.reservedName", ex.getMessage());
    }

    @Test
    void reservedNameDeepInPathRejected() throws IOException {
        // Reserved-name check runs per segment, not just the basename, because Windows refuses
        // any path component that matches a reserved device name (CON, PRN, etc.) regardless of
        // depth. Lock the per-segment behaviour so the validator stays Windows-safe.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/COM1/index.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.reservedName", ex.getMessage());
    }

    @Test
    void overlongFilenameRejected() throws IOException {
        // 240-char per-entry-path cap. Probe locks the boundary so a future bump doesn't
        // accidentally remove the cap entirely (which would let an attacker push extracted
        // paths past Windows MAX_PATH and create extraction failures or filesystem havoc).
        StringBuilder longName = new StringBuilder("templates/");
        while (longName.length() < 250) {
            longName.append('a');
        }
        longName.append(".html");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(longName.toString(), bytes("hi"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.invalidEntryName", ex.getMessage());
    }

    @Test
    void driveLetterAbsolutePathRejected() throws IOException {
        // Windows-style C:\... entries land before the absolute-path check (no leading /), so
        // a separate rule catches them. Lock the rejection.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("C:/windows/system32/config", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.absolutePath", ex.getMessage());
    }

    @Test
    void tooManyEntriesRejected() throws IOException {
        // The validator caps at MAX_ENTRIES (10 000). Build a zip with 10 001 small entries and
        // confirm the limit fires before the validator OOMs the metadata list.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        // The count check trips first, so no template entry is needed. Generate 10 001 entries
        // of trivial content.
        for (int i = 0; i < 10_001; i++) {
            entries.put("file" + i + ".txt", new byte[]{0});
        }
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.tooManyEntries", ex.getMessage());
    }

    @Test
    void perEntrySizeBombRejected() throws IOException {
        // A single entry bigger than the per-entry cap (100 MB) trips before the running total
        // can be poisoned. Use a smaller artificial max to keep the test fast — point a small
        // total max at a smaller entry that exceeds it. The total-size guard also fires here,
        // so we just confirm the error key is one of the two expected size errors.
        long smallMax = 64 * 1024;            // 64 KiB total budget
        byte[] big = new byte[(int) smallMax + 1024];
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", big);
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, smallMax));
        assertEquals("theme.install.error.tooLarge", ex.getMessage());
    }

    @Test
    void oversizedTemplateJsonRejected() throws IOException {
        // theme.manifest.json is captured into memory (up to 64 KiB). A theme that ships a 128 KiB
        // theme.manifest.json must be rejected so the validator never holds an unbounded byte buffer.
        byte[] payload = new byte[64 * 1024 + 1];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = '"';
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("theme.manifest.json", payload);
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.metadataTooLarge", ex.getMessage());
    }

    @Test
    void zipBombStyleRatioStillBoundedByTotal() throws IOException {
        // High-ratio zip bomb: a payload that compresses well sits inside one entry. The
        // validator decompresses incrementally and trips the total-size cap rather than the
        // entry cap. Use 4 MiB of zeros (compresses ~1000:1) against a 1 MiB total cap.
        long oneMb = 1024L * 1024L;
        byte[] zeros = new byte[4 * 1024 * 1024];
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", zeros);
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, oneMb));
        assertEquals("theme.install.error.tooLarge", ex.getMessage());
    }

    @Test
    void unicodeEntryNamesAccepted() throws IOException {
        // Non-ASCII Unicode in entry names is fine — themes ship Chinese / Japanese filenames
        // for documentation and this is not a security boundary. Lock the acceptance so the
        // path-validation regex doesn't accidentally start rejecting wide characters.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("snippets/页眉.html", bytes("partial"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    @Test
    void crlfInEntryNameAcceptedAsLiteral() throws IOException {
        // CRLF in entry names is not exploitable via the validator (which never echoes the raw
        // name to a header) but ZipOutputStream accepts it, so we lock that the validator passes
        // it through cleanly. The extractor is the next gate; on Windows NTFS rejects CR/LF in
        // names, so the actual write fails. This split keeps the validator focused on path
        // traversal and leaves filesystem-character validity to the OS.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("snippets/foo\r\nbar.html", bytes("partial"));
        byte[] zip = zipOf(entries);
        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    // ----- §12.1 composition dirs JSON-only rule -----

    @Test
    void compositionDirRejectsStrayRendererFile() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("pages/home.manifest.json", bytes("{}"));
        entries.put("pages/sneaky.html", bytes("<p>nope</p>")); // stray renderer in pages/
        byte[] zip = zipOf(entries);
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.compositionNonJson", ex.getMessage());
    }

    @Test
    void compositionDirRejectsStrayHtmlInShapes() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("shapes/promo.json", bytes("{}"));
        entries.put("shapes/notes.html", bytes("<p>nope</p>"));
        byte[] zip = zipOf(entries);
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.compositionNonJson", ex.getMessage());
    }

    @Test
    void compositionDirsWithOnlyJsonAreAccepted() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("pages/home.manifest.json", bytes("{}"));
        entries.put("shapes/promo.json", bytes("{}"));
        entries.put("seed/cards/promo.cards.json", bytes("{}"));
        entries.put("i18n/zh-CN.json", bytes("{}"));
        byte[] zip = zipOf(entries);
        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getTemplateFileCount());
    }

    // ----- §8 data-only packages (A5): accept/reject matrix -----

    private static final String DATA_ONLY_MANIFEST =
            "{\"name\":\"SSR Demo\",\"themeName\":\"ssr-demo\",\"version\":\"1.0.0\","
                    + "\"templateSpecVersion\":\"2.0\",\"kind\":\"data-only\"}";

    @Test
    void dataOnlySeedPackageAccepted() throws IOException {
        // 纯数据包：清单 + 种子（含 assets 媒体豁免）+ 画廊截图，零模板——通过
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("seed/content/assets/img/ch.svg", bytes("<svg/>"));
        entries.put("seed/regions/home.regions.json", bytes("{}"));
        entries.put("preview.svg", bytes("<svg/>"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(0, report.getTemplateFileCount());
        assertTrue(report.isHasManifest());
        assertNotNull(report.getMetadata());
        assertTrue(report.getMetadata().isDataOnly());
        assertTrue(report.getWarnings().isEmpty());
    }

    @Test
    void dataOnlyWithRootTemplateRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("index.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.dataOnlyHasTemplates", ex.getMessage());
    }

    @Test
    void dataOnlyWithNestedTemplateRejected() throws IOException {
        // 主题包的模板规则只认 templates/，纯数据包的禁令更宽：seed/ 与资产目录之外
        // 任何位置的 .html 都拒绝（snippets/ 里的局部模板同样是渲染器）。
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("snippets/header.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.dataOnlyHasTemplates", ex.getMessage());
    }

    @Test
    void dataOnlyStillEnforcesJsonOnlyRule() throws IOException {
        // JSON-only 构成目录规则（含 seed/content/assets/** 例外）对纯数据包原样生效
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/cards/readme.md", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.compositionNonJson", ex.getMessage());
    }

    @Test
    void dataOnlyStillEnforcesZipSlip() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/../escape.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.zipSlip", ex.getMessage());
    }

    @Test
    void dataOnlyStillEnforcesSizeCap() throws IOException {
        long smallMax = 1024;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/assets/img/big.svg", new byte[(int) smallMax + 100]);
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, smallMax));
        assertEquals("theme.install.error.tooLarge", ex.getMessage());
    }

    @Test
    void unknownKindRejected() throws IOException {
        // 安装门槛从严（A0 dev #5 精神：显式解析、未知值 400 而非忽略）
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("theme.manifest.json",
                bytes("{\"name\":\"x\",\"version\":\"1\",\"kind\":\"datapack\"}"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.unknownKind", ex.getMessage());
    }

    @Test
    void explicitThemeKindStillRequiresTemplates() throws IOException {
        // kind: theme（显式）不改变既有结构规则——零模板仍拒绝
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json",
                bytes("{\"name\":\"x\",\"version\":\"1\",\"kind\":\"theme\"}"));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> validator.validate(stream(zip), zip.length, MAX_BYTES));
        assertEquals("theme.install.error.missingTemplate", ex.getMessage());
    }

    @Test
    void dataOnlyWithPagesOrShapesWarns() throws IOException {
        // 随包 pages/shapes 合法（JSON-only 管辖）但仅对生效主题起作用——告警不拒绝
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("shapes/promo.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = validator.validate(stream(zip), zip.length, MAX_BYTES);
        assertEquals(1, report.getWarnings().size());
    }

}
