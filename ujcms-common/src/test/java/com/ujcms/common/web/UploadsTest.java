package com.ujcms.common.web;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传文件命名：时间戳 + 12 位随机数，按月归档；头像等自带目录的上传用不含目录的同一套名字
 */
class UploadsTest {
    private static final String NAME = "\\d{17}_[a-z0-9]{12}";

    @Test
    void randomNameCarriesStampRandomAndExtension() {
        String name = Uploads.getRandomName("png");
        assertTrue(name.matches("^" + NAME + "\\.png$"), name);
    }

    @Test
    void randomNameWithoutExtensionHasNoDot() {
        assertTrue(Uploads.getRandomName(null).matches("^" + NAME + "$"));
        assertTrue(Uploads.getRandomName(" ").matches("^" + NAME + "$"));
    }

    @Test
    void randomFilenameArchivesByMonthOfTheSameStamp() {
        String filename = Uploads.getRandomFilename("jpg");
        assertTrue(filename.matches("^/\\d{4}/\\d{2}/" + NAME + "\\.jpg$"), filename);
        // 目录段 /yyyy/MM 与文件名开头的 yyyyMM 取自同一时刻
        assertEquals(filename.substring(1, 5) + filename.substring(6, 8), filename.substring(9, 15), filename);
    }

    @Test
    void randomNamesDoNotCollide() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            names.add(Uploads.getRandomName("png"));
        }
        assertEquals(1000, names.size());
    }
}
