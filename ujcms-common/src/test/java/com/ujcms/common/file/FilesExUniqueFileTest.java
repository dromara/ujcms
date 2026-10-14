package com.ujcms.common.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link FilesEx#getUniqueFile}：文件不存在时原样返回；已存在时在主文件名后追加 (1)、(2)……，扩展名不变。只确定名称，不创建文件。
 */
class FilesExUniqueFileTest {
    @TempDir
    Path dir;

    private File file(String name) {
        return dir.resolve(name).toFile();
    }

    private void occupy(String... names) throws IOException {
        for (String name : names) {
            Files.createFile(dir.resolve(name));
        }
    }

    @Test
    void missingFileIsReturnedAsIs() {
        File wanted = file("a.txt");
        assertSame(wanted, FilesEx.getUniqueFile(wanted));
        assertFalse(wanted.exists());
    }

    @Test
    void takenNameGetsTheFirstIndex() throws IOException {
        occupy("a.txt");
        File unique = FilesEx.getUniqueFile(file("a.txt"));
        assertEquals(file("a(1).txt"), unique);
        assertFalse(unique.exists());
    }

    @Test
    void takenIndexesAreSkipped() throws IOException {
        occupy("a.txt", "a(1).txt");
        assertEquals(file("a(2).txt"), FilesEx.getUniqueFile(file("a.txt")));
    }

    @Test
    void nameWithoutExtensionGetsNoDot() throws IOException {
        occupy("readme");
        assertEquals(file("readme(1)"), FilesEx.getUniqueFile(file("readme")));
    }

    /**
     * 空白扩展名（文件名以点加空格结尾）视同没有扩展名。Windows 不允许文件名以空格结尾，因此在 Windows 上跳过。
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void blankExtensionIsDropped() throws IOException {
        occupy("a. ");
        assertEquals(file("a(1)"), FilesEx.getUniqueFile(file("a. ")));
    }
}
