package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 子行（扩展栏目、推送、译文）用的扩展数据（docs/spec/article-management/plan.md Phase 1.3）：内容照搬，只属于原文章
 * 自己的状态清零。静态页路径带到子行上，子行重新生成或被删除时删掉的是原文章的页面（H4）。
 */
class ArticleExtTest {
    @Test
    void copyForChildKeepsTheContentAndDropsWhatBelongsToTheSource() {
        ArticleExt source = new ArticleExt();
        source.setId(1L);
        source.setTitle("title");
        source.setText("<p>body</p>");
        source.setMainsJson("{\"a\":1}");
        source.setAiGenerated(true);
        source.setStaticFile("/1/article/1.html");
        source.setMobileStaticFile("/1/m/article/1.html");
        source.setProcessInstanceId("p1");
        source.setRejectReason("fix the title");
        source.setComments(1);
        source.setDownloads(2);
        source.setFavorites(3);
        source.setUps(4);
        source.setDowns(5);
        source.setViews(6L);
        source.setDayViews(7);
        source.setWeekViews(8);
        source.setMonthViews(9);
        source.setQuarterViews(10);
        source.setYearViews(11L);

        ArticleExt child = source.copyForChild();

        assertNotSame(source, child);
        assertEquals("title", child.getTitle());
        assertEquals("<p>body</p>", child.getText());
        assertEquals("{\"a\":1}", child.getMainsJson());
        assertEquals(true, child.getAiGenerated());
        assertNull(child.getId());
        assertNull(child.getStaticFile());
        assertNull(child.getMobileStaticFile());
        assertNull(child.getProcessInstanceId());
        assertNull(child.getRejectReason());
        assertEquals(0, child.getComments());
        assertEquals(0, child.getDownloads());
        assertEquals(0, child.getFavorites());
        assertEquals(0, child.getUps());
        assertEquals(0, child.getDowns());
        assertEquals(0L, child.getViews());
        assertEquals(0, child.getDayViews());
        assertEquals(0, child.getWeekViews());
        assertEquals(0, child.getMonthViews());
        assertEquals(0, child.getQuarterViews());
        assertEquals(0L, child.getYearViews());
        // 原对象不受影响
        assertEquals("/1/article/1.html", source.getStaticFile());
        assertEquals(6L, source.getViews());
    }
}
