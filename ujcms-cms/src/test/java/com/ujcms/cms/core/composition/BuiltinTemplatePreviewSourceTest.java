package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.TagArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.cms.ext.service.args.VoteArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核心内置实例页预览来源测试（preview-url-resolver P4）：标签 / 投票 / 留言（功能开关 + 可见性）/
 * 会员主页（以当前操作员为代表）。
 */
class BuiltinTemplatePreviewSourceTest {
    private TagService tagService;
    private VoteService voteService;
    private MessageBoardService messageBoardService;
    private BuiltinTemplatePreviewSource source;
    private Site site;
    private Site.MessageBoard messageBoardSettings;

    @BeforeEach
    void setUp() {
        tagService = mock(TagService.class);
        voteService = mock(VoteService.class);
        messageBoardService = mock(MessageBoardService.class);
        source = new BuiltinTemplatePreviewSource(tagService, voteService, messageBoardService);
        site = mock(Site.class);
        when(site.getId()).thenReturn(1L);
        when(site.getDynamicUrl()).thenReturn("/");
        messageBoardSettings = new Site.MessageBoard();
        messageBoardSettings.setEnabled(true);
        when(site.getMessageBoard()).thenReturn(messageBoardSettings);
        when(tagService.selectList(any(TagArgs.class), anyInt(), anyInt())).thenReturn(List.of());
        when(voteService.selectList(any(VoteArgs.class), anyInt(), anyInt())).thenReturn(List.of());
        when(messageBoardService.selectList(any(MessageBoardArgs.class), anyInt(), anyInt())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentUser();
    }

    @Test
    void tagItemUsesSiteTags() {
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("科技");
        when(tagService.selectList(any(TagArgs.class), anyInt(), anyInt())).thenReturn(List.of(tag));
        PreviewUrlResolver.Result result = source.find(site, "sys_tag_item", 1);
        assertNull(result.emptyReason());
        PreviewUrlResolver.Candidate candidate = result.candidates().get(0);
        assertEquals("/tag/7", candidate.url());
        assertEquals(PreviewUrlResolver.SUBJECT_TAG, candidate.kind());
        assertEquals("科技", candidate.label());
        when(tagService.selectList(any(TagArgs.class), anyInt(), anyInt())).thenReturn(List.of());
        PreviewUrlResolver.Result empty = source.find(site, "sys_tag_item", 1);
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT, empty.emptyReason());
        assertEquals(PreviewUrlResolver.SUBJECT_TAG, empty.emptySubject());
    }

    @Test
    void voteShowAndResultUseEnabledVotes() {
        Vote vote = new Vote();
        vote.setId(3L);
        vote.setTitle("满意度");
        when(voteService.selectList(any(VoteArgs.class), anyInt(), anyInt())).thenReturn(List.of(vote));
        assertEquals("/vote/3", source.find(site, "sys_vote_show", 1).candidates().get(0).url());
        assertEquals("/vote/3/result", source.find(site, "sys_vote_result", 1).candidates().get(0).url());
    }

    @Test
    void messageBoardItemHonoursSwitchAndVisibility() {
        messageBoardSettings.setEnabled(false);
        PreviewUrlResolver.Result off = source.find(site, "sys_message_board_item", 1);
        assertEquals(PreviewUrlResolver.EMPTY_DISABLED, off.emptyReason());
        assertEquals(PreviewUrlResolver.SUBJECT_MESSAGE_BOARD, off.emptySubject());
        verify(messageBoardService, never()).selectList(any(MessageBoardArgs.class), anyInt(), anyInt());
        messageBoardSettings.setEnabled(true);
        MessageBoard visible = new MessageBoard();
        visible.setId(9L);
        visible.setTitle("建议");
        visible.setStatus(MessageBoard.STATUS_AUDITED);
        visible.setOpen(true);
        MessageBoard hidden = new MessageBoard();
        hidden.setId(10L);
        hidden.setStatus(MessageBoard.STATUS_AUDITED);
        hidden.setOpen(false);
        when(messageBoardService.selectList(any(MessageBoardArgs.class), anyInt(), anyInt()))
                .thenReturn(List.of(hidden, visible));
        List<String> urls = source.find(site, "sys_message_board_item", 5).candidates().stream()
                .map(PreviewUrlResolver.Candidate::url).toList();
        assertEquals(List.of("/message-board/9"), urls);
    }

    @Test
    void memberHomeUsesCurrentOperator() {
        PreviewUrlResolver.Result anonymous = source.find(site, "mem_user", 1);
        assertTrue(anonymous.candidates().isEmpty());
        assertEquals(PreviewUrlResolver.SUBJECT_USER, anonymous.emptySubject());
        User viewer = mock(User.class);
        when(viewer.getId()).thenReturn(5L);
        when(viewer.getUsername()).thenReturn("admin");
        Contexts.setCurrentUser(viewer);
        PreviewUrlResolver.Candidate candidate = source.find(site, "mem_user", 1).candidates().get(0);
        assertEquals("/users/5", candidate.url());
        assertEquals("admin", candidate.label());
        when(viewer.isCancelled()).thenReturn(true);
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT, source.find(site, "mem_user", 1).emptyReason());
    }
}
