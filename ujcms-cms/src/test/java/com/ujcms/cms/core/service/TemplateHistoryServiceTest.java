package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.TemplateHistory;
import com.ujcms.cms.core.mapper.TemplateHistoryMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板编辑历史服务测试（template-history plan.md §4–§8）：捕获资格（扩展名/大小/路径长度）、
 * 回填漂移、追加去重、保留裁剪、删除捕获（含目录递归）、恢复（含已删除文件重建与溯源）。
 * 文件读写经 mock {@link FileHandler}（getWebFile / writeOutputStream / store / listFiles
 * 可移植原语——服务只允许用这些）。
 */
class TemplateHistoryServiceTest {
    private static final String PATH = "/1/scratch/templates/index.html";

    private TemplateHistoryMapper mapper;
    private FileHandler fileHandler;
    private TemplateHistoryService service;
    /**
     * mapper.insert 的落库回显：insert 记录于此，selectLatestByPath 返回最后一条（模拟只追加表）
     */
    private final List<TemplateHistory> inserted = new ArrayList<>();
    /**
     * 模拟存储中的文件内容（getWebFile / writeOutputStream 的数据源）
     */
    private final Map<String, String> files = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(TemplateHistoryMapper.class);
        fileHandler = mock(FileHandler.class);
        inserted.clear();
        files.clear();
        when(mapper.insert(any(TemplateHistory.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return 1;
        });
        when(mapper.selectLatestByPath(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            for (int i = inserted.size() - 1; i >= 0; i--) {
                if (inserted.get(i).getPath().equals(path)) {
                    return inserted.get(i);
                }
            }
            return null;
        });
        when(fileHandler.getWebFile(anyString())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            String content = files.get(path);
            if (content == null) {
                return null;
            }
            WebFile webFile = mock(WebFile.class);
            when(webFile.isDirectory()).thenReturn(false);
            when(webFile.getLength()).thenReturn((long) content.getBytes(StandardCharsets.UTF_8).length);
            when(webFile.getId()).thenReturn(path);
            return webFile;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            String path = inv.getArgument(0);
            OutputStream out = inv.getArgument(1);
            String content = files.get(path);
            if (content != null) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }).when(fileHandler).writeOutputStream(anyString(), any(OutputStream.class));
        service = new TemplateHistoryService(mapper, new SnowflakeSequence(0, 0));
    }

    private List<TemplateHistory> entries(String path) {
        return inserted.stream().filter(e -> e.getPath().equals(path)).toList();
    }

    // ----- 资格 -----------------------------------------------------------

    @Test
    void binaryExtensionIsIneligible() {
        assertTrue(service.isEligiblePath(PATH));
        assertTrue(service.isEligiblePath("/1/scratch/theme.manifest.json"));
        assertTrue(service.isEligiblePath("/1/scratch/assets/css/style.css"));
        org.junit.jupiter.api.Assertions.assertFalse(service.isEligiblePath("/1/scratch/assets/img/logo.png"));
        org.junit.jupiter.api.Assertions.assertFalse(service.isEligiblePath("/1/scratch/bundle.zip"));
    }

    @Test
    void tooLongPathIsIneligibleAndNothingRecorded() {
        String longPath = "/1/" + "a".repeat(TemplateHistoryService.MAX_PATH_LENGTH) + ".html";
        service.recordWrite(longPath, null, "x", TemplateHistory.SOURCE_SAVE, "admin", null);
        assertTrue(inserted.isEmpty());
    }

    @Test
    void oversizeFileReadsAsNull() {
        WebFile big = mock(WebFile.class);
        when(big.isDirectory()).thenReturn(false);
        when(big.getLength()).thenReturn((long) TemplateHistoryService.MAX_CONTENT_BYTES + 1);
        when(fileHandler.getWebFile(PATH)).thenReturn(big);
        assertNull(service.readEligibleText(fileHandler, PATH));
    }

    @Test
    void oversizeNewContentSkippedButPreStateStillBackfilled() {
        String pre = "old";
        String huge = "x".repeat(TemplateHistoryService.MAX_CONTENT_BYTES + 1);
        service.recordWrite(PATH, pre, huge, TemplateHistory.SOURCE_UPLOAD, "admin", null);
        List<TemplateHistory> list = entries(PATH);
        assertEquals(1, list.size());
        assertEquals(TemplateHistory.SOURCE_BACKFILL, list.get(0).getSource());
        assertEquals(pre, list.get(0).getContent());
    }

    // ----- 保存：回填 + 追加 + 去重 ----------------------------------------

    @Test
    void firstSaveAppendsSingleEntry() {
        service.recordWrite(PATH, null, "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        List<TemplateHistory> list = entries(PATH);
        assertEquals(1, list.size());
        assertEquals(TemplateHistory.SOURCE_SAVE, list.get(0).getSource());
        assertEquals("v1", list.get(0).getContent());
        assertEquals(2, list.get(0).getLength());
        assertEquals("admin", list.get(0).getActor());
    }

    @Test
    void driftedPreStateIsBackfilledBeforeSave() {
        // 特性上线前存量/旁路写入：历史为空但文件已有内容
        service.recordWrite(PATH, "legacy", "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        List<TemplateHistory> list = entries(PATH);
        assertEquals(2, list.size());
        assertEquals(TemplateHistory.SOURCE_BACKFILL, list.get(0).getSource());
        assertEquals("legacy", list.get(0).getContent());
        assertEquals(TemplateHistory.SOURCE_SAVE, list.get(1).getSource());
    }

    @Test
    void unchangedPreStateIsNotBackfilledTwice() {
        service.recordWrite(PATH, null, "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        // 正常编辑循环：写前状态 == 最新条目，不回填
        service.recordWrite(PATH, "v1", "v2", TemplateHistory.SOURCE_SAVE, "admin", null);
        List<TemplateHistory> list = entries(PATH);
        assertEquals(2, list.size());
        assertEquals("v1", list.get(0).getContent());
        assertEquals("v2", list.get(1).getContent());
    }

    @Test
    void identicalSaveIsDeduplicated() {
        service.recordWrite(PATH, null, "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        service.recordWrite(PATH, "v1", "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        assertEquals(1, entries(PATH).size());
    }

    // ----- 裁剪 -----------------------------------------------------------

    @Test
    void trimDeletesBeyondRetention() {
        List<TemplateHistory> stale = new ArrayList<>();
        for (int i = 0; i < TemplateHistoryService.HISTORY_RETENTION + 2; i++) {
            TemplateHistory h = new TemplateHistory();
            h.setId((long) (1000 - i));
            h.setPath(PATH);
            stale.add(h);
        }
        when(mapper.listByPath(PATH)).thenReturn(stale);
        service.recordWrite(PATH, null, "v1", TemplateHistory.SOURCE_SAVE, "admin", null);
        // 新→旧列表中第 50 条之后的两条被删除
        verify(mapper).delete(stale.get(TemplateHistoryService.HISTORY_RETENTION).getId());
        verify(mapper).delete(stale.get(TemplateHistoryService.HISTORY_RETENTION + 1).getId());
    }

    // ----- 删除捕获 --------------------------------------------------------

    @Test
    void collectForDeleteCapturesEligibleFileContent() {
        files.put(PATH, "doomed");
        List<TemplateHistoryService.CapturedFile> captured = service.collectForDelete(fileHandler, PATH);
        assertEquals(1, captured.size());
        assertEquals(PATH, captured.get(0).path());
        assertEquals("doomed", captured.get(0).content());
    }

    @Test
    void collectForDeleteWalksDirectoriesRecursively() {
        String dir = "/1/scratch";
        String sub = "/1/scratch/templates";
        files.put(PATH, "doomed");
        WebFile dirFile = mock(WebFile.class);
        when(dirFile.isDirectory()).thenReturn(true);
        WebFile subFile = mock(WebFile.class);
        when(subFile.isDirectory()).thenReturn(true);
        when(subFile.getId()).thenReturn(sub);
        WebFile binary = mock(WebFile.class);
        when(binary.isDirectory()).thenReturn(false);
        when(binary.getId()).thenReturn("/1/scratch/logo.png");
        WebFile leaf = mock(WebFile.class);
        when(leaf.isDirectory()).thenReturn(false);
        when(leaf.getId()).thenReturn(PATH);
        when(fileHandler.getWebFile(dir)).thenReturn(dirFile);
        when(fileHandler.getWebFile(sub)).thenReturn(subFile);
        when(fileHandler.listFiles(dir)).thenReturn(List.of(subFile, binary));
        when(fileHandler.listFiles(sub)).thenReturn(List.of(leaf));
        List<TemplateHistoryService.CapturedFile> captured = service.collectForDelete(fileHandler, dir);
        // 二进制文件跳过，子目录内的文本文件捕获
        assertEquals(1, captured.size());
        assertEquals(PATH, captured.get(0).path());
    }

    @Test
    void collectForWipeWalksAllTopLevelEntries() {
        // 模板备份恢复的清空阶段（plan §12）：根下逐顶层条目捕获——目录递归、根级文件直取、二进制跳过
        String siteDir = "/1";
        String rootFile = "/error.html";
        files.put(PATH, "themed");
        files.put(rootFile, "root");
        WebFile siteDirFile = mock(WebFile.class);
        when(siteDirFile.isDirectory()).thenReturn(true);
        when(siteDirFile.getId()).thenReturn(siteDir);
        WebFile leaf = mock(WebFile.class);
        when(leaf.isDirectory()).thenReturn(false);
        when(leaf.getId()).thenReturn(PATH);
        WebFile rootLeaf = mock(WebFile.class);
        when(rootLeaf.isDirectory()).thenReturn(false);
        when(rootLeaf.getId()).thenReturn(rootFile);
        WebFile binary = mock(WebFile.class);
        when(binary.isDirectory()).thenReturn(false);
        when(binary.getId()).thenReturn("/favicon.ico");
        when(fileHandler.getWebFile(siteDir)).thenReturn(siteDirFile);
        when(fileHandler.listFiles("/")).thenReturn(List.of(siteDirFile, rootLeaf, binary));
        when(fileHandler.listFiles(siteDir)).thenReturn(List.of(leaf));
        List<TemplateHistoryService.CapturedFile> captured = service.collectForWipe(fileHandler);
        assertEquals(2, captured.size());
        assertEquals(PATH, captured.get(0).path());
        assertEquals("themed", captured.get(0).content());
        assertEquals(rootFile, captured.get(1).path());
        assertEquals("root", captured.get(1).content());
    }

    @Test
    void collectSkipsDevArtifactDirectories() {
        // node_modules/_src 等构建产物整树跳过：其 .js/.json 文本会吞掉 500 文件预算并污染历史表
        String dir = "/1/scratch";
        String nodeModules = "/1/scratch/node_modules";
        String junk = "/1/scratch/node_modules/lodash/index.js";
        files.put(PATH, "kept");
        files.put(junk, "junk");
        WebFile dirFile = mock(WebFile.class);
        when(dirFile.isDirectory()).thenReturn(true);
        WebFile nodeModulesDir = mock(WebFile.class);
        when(nodeModulesDir.isDirectory()).thenReturn(true);
        when(nodeModulesDir.getId()).thenReturn(nodeModules);
        WebFile leaf = mock(WebFile.class);
        when(leaf.isDirectory()).thenReturn(false);
        when(leaf.getId()).thenReturn(PATH);
        when(fileHandler.getWebFile(dir)).thenReturn(dirFile);
        when(fileHandler.getWebFile(nodeModules)).thenReturn(nodeModulesDir);
        when(fileHandler.listFiles(dir)).thenReturn(List.of(nodeModulesDir, leaf));
        List<TemplateHistoryService.CapturedFile> captured = service.collectForDelete(fileHandler, dir);
        assertEquals(1, captured.size());
        assertEquals(PATH, captured.get(0).path());
        verify(fileHandler, never()).listFiles(nodeModules);
    }

    @Test
    void recordDeletesAppendsDeleteEntriesWithDedup() {
        service.recordWrite(PATH, null, "doomed", TemplateHistory.SOURCE_SAVE, "admin", null);
        // 与最新条目内容一致 → 去重跳过
        service.recordDeletes(List.of(new TemplateHistoryService.CapturedFile(PATH, "doomed")), "admin");
        assertEquals(1, entries(PATH).size());
        // 旁路修改过（漂移）→ 追加 delete 条目
        service.recordDeletes(List.of(new TemplateHistoryService.CapturedFile(PATH, "drifted")), "admin");
        List<TemplateHistory> list = entries(PATH);
        assertEquals(2, list.size());
        assertEquals(TemplateHistory.SOURCE_DELETE, list.get(1).getSource());
        assertEquals("drifted", list.get(1).getContent());
    }

    // ----- 恢复 -----------------------------------------------------------

    @Test
    void restoreWritesThroughHandlerAndAppendsProvenance() {
        TemplateHistory entry = new TemplateHistory();
        entry.setId(42L);
        entry.setPath(PATH);
        entry.setContent("good");
        files.put(PATH, "broken");
        service.restore(fileHandler, entry, "admin");
        verify(fileHandler).store(PATH, "good");
        List<TemplateHistory> list = entries(PATH);
        // 被替换的坏状态先回填，恢复条目带溯源
        assertEquals(2, list.size());
        assertEquals(TemplateHistory.SOURCE_BACKFILL, list.get(0).getSource());
        assertEquals("broken", list.get(0).getContent());
        assertEquals(TemplateHistory.SOURCE_RESTORE, list.get(1).getSource());
        assertEquals("good", list.get(1).getContent());
        assertEquals(Long.valueOf(42L), list.get(1).getRestoredFrom());
    }

    @Test
    void restoreRecreatesDeletedFile() {
        TemplateHistory entry = new TemplateHistory();
        entry.setId(43L);
        entry.setPath(PATH);
        entry.setContent("resurrected");
        // 文件不存在（已删除）——store 重建，无回填
        service.restore(fileHandler, entry, "admin");
        verify(fileHandler).store(PATH, "resurrected");
        List<TemplateHistory> list = entries(PATH);
        assertEquals(1, list.size());
        assertEquals(TemplateHistory.SOURCE_RESTORE, list.get(0).getSource());
    }

    @Test
    void restoreOfIneligiblePathRejected() {
        TemplateHistory entry = new TemplateHistory();
        entry.setId(44L);
        entry.setPath("/1/scratch/logo.png");
        entry.setContent("x");
        assertThrows(Http400Exception.class, () -> service.restore(fileHandler, entry, "admin"));
        verify(fileHandler, never()).store(anyString(), anyString());
    }

    @Test
    void getMissingEntryThrows() {
        when(mapper.select(9L)).thenReturn(null);
        assertThrows(Http400Exception.class, () -> service.get(9L));
    }

    @Test
    void appendPopulatesLengthAndActor() {
        service.recordWrite(PATH, null, "中文", TemplateHistory.SOURCE_SAVE, "editor", null);
        ArgumentCaptor<TemplateHistory> captor = ArgumentCaptor.forClass(TemplateHistory.class);
        verify(mapper, atLeastOnce()).insert(captor.capture());
        TemplateHistory bean = captor.getValue();
        assertEquals("中文".getBytes(StandardCharsets.UTF_8).length, bean.getLength());
        assertEquals("editor", bean.getActor());
        assertEquals(PATH, bean.getPath());
    }
}
