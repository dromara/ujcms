package com.ujcms.cms.core.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安装令牌文件（install-flow D49）：写入 / 路径报告 / 删除 / 写入失败不抛出。
 */
class InstallTokenFileTest {

    @TempDir
    Path dir;

    @Test
    void writeReportsPathAndStoresPlaintext() throws IOException {
        Path file = dir.resolve("WEB-INF").resolve("install-token.txt");
        InstallTokenFile tokenFile = new InstallTokenFile(() -> file);

        assertNull(tokenFile.existingPath(), "no file before write");
        Path written = tokenFile.write("abc123");

        assertNotNull(written);
        assertEquals(file.toAbsolutePath(), written);
        assertEquals("abc123", Files.readString(file, StandardCharsets.UTF_8).trim());
        assertEquals(file.toAbsolutePath().toString(), tokenFile.existingPath());
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms);
        }
    }

    @Test
    void rewriteReplacesPreviousToken() throws IOException {
        Path file = dir.resolve("install-token.txt");
        InstallTokenFile tokenFile = new InstallTokenFile(() -> file);
        tokenFile.write("first");
        tokenFile.write("second");
        assertEquals("second", Files.readString(file, StandardCharsets.UTF_8).trim());
    }

    @Test
    void deleteIsIdempotent() {
        Path file = dir.resolve("install-token.txt");
        InstallTokenFile tokenFile = new InstallTokenFile(() -> file);
        tokenFile.write("abc");
        tokenFile.delete();
        assertFalse(Files.exists(file));
        assertNull(tokenFile.existingPath());
        tokenFile.delete();
        assertFalse(Files.exists(file));
    }

    @Test
    void writeFailureReturnsNullWithoutThrowing() throws IOException {
        // 目标路径的父级是一个普通文件：createDirectories 必然失败
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        Path file = blocker.resolve("install-token.txt");
        InstallTokenFile tokenFile = new InstallTokenFile(() -> file);

        assertNull(tokenFile.write("abc"));
        assertNull(tokenFile.existingPath());
        tokenFile.delete();
        assertTrue(Files.exists(blocker));
    }
}
