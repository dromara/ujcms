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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.ujcms.cms.core.composition.PreviewUrlResolver.Candidate;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.EMPTY_DISABLED;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.EMPTY_NO_CONTENT;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.Result;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.SUBJECT_MESSAGE_BOARD;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.SUBJECT_TAG;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.SUBJECT_USER;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.SUBJECT_VOTE;
import static com.ujcms.cms.core.composition.PreviewUrlResolver.sitePath;

/**
 * 核心内置的实例页预览来源（preview-url-resolver §3.4 / P4）：核心前台控制器渲染、以某条记录为上下文的
 * 系统模板。渲染同径：
 *
 * <ul>
 * <li>{@code sys_tag_item} → {@code /tag/{id}}（{@code TagController#show}：存在即渲染）</li>
 * <li>{@code sys_vote_show} / {@code sys_vote_result} → {@code /vote/{id}} / {@code /vote/{id}/result}
 * （{@code VoteController}：仅启用的投票）</li>
 * <li>{@code sys_message_board_item} → {@code /message-board/{id}}（{@code MessageBoardController#show}：
 * 站点留言板已启用，且留言已审核并公开）</li>
 * <li>{@code mem_user} → {@code /users/{id}}（{@code MemberController#users}：未注销用户即可——以当前操作员
 * 自己为代表，不枚举他人）</li>
 * </ul>
 *
 * @author PONY
 */
@Component
public class BuiltinTemplatePreviewSource implements TemplatePreviewSource {
    private static final String TEMPLATE_TAG_ITEM = "sys_tag_item";
    private static final String TEMPLATE_VOTE_SHOW = "sys_vote_show";
    private static final String TEMPLATE_VOTE_RESULT = "sys_vote_result";
    private static final String TEMPLATE_MESSAGE_BOARD_ITEM = "sys_message_board_item";
    private static final String TEMPLATE_USER = "mem_user";

    private static final Set<String> TEMPLATES = Set.of(TEMPLATE_TAG_ITEM, TEMPLATE_VOTE_SHOW,
            TEMPLATE_VOTE_RESULT, TEMPLATE_MESSAGE_BOARD_ITEM, TEMPLATE_USER);

    private final TagService tagService;
    private final VoteService voteService;
    private final MessageBoardService messageBoardService;

    public BuiltinTemplatePreviewSource(TagService tagService, VoteService voteService,
                                        MessageBoardService messageBoardService) {
        this.tagService = tagService;
        this.voteService = voteService;
        this.messageBoardService = messageBoardService;
    }

    @Override
    public Set<String> templates() {
        return TEMPLATES;
    }

    @Override
    public Result find(Site site, String template, int limit) {
        return switch (template) {
            case TEMPLATE_TAG_ITEM -> tags(site, template, limit);
            case TEMPLATE_VOTE_SHOW -> votes(site, template, "", limit);
            case TEMPLATE_VOTE_RESULT -> votes(site, template, "/result", limit);
            case TEMPLATE_MESSAGE_BOARD_ITEM -> messageBoards(site, template, limit);
            case TEMPLATE_USER -> user(site, template);
            default -> new Result(List.of(), EMPTY_NO_CONTENT);
        };
    }

    private Result tags(Site site, String template, int limit) {
        List<Tag> tags = tagService.selectList(TagArgs.of().siteId(site.getId()), 0, limit);
        List<Candidate> candidates = tags.stream()
                .map(tag -> new Candidate(SUBJECT_TAG, tag.getName(), sitePath(site, "/tag/" + tag.getId()),
                        String.valueOf(tag.getId()), template, null))
                .toList();
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null, SUBJECT_TAG);
    }

    private Result votes(Site site, String template, String suffix, int limit) {
        List<Vote> votes = voteService.selectList(VoteArgs.of().siteId(site.getId()).enabled(true), 0, limit);
        List<Candidate> candidates = votes.stream()
                .map(vote -> new Candidate(SUBJECT_VOTE, vote.getTitle(),
                        sitePath(site, "/vote/" + vote.getId() + suffix), String.valueOf(vote.getId()),
                        template, null))
                .toList();
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null, SUBJECT_VOTE);
    }

    private Result messageBoards(Site site, String template, int limit) {
        if (!site.getMessageBoard().isEnabled()) {
            return new Result(List.of(), EMPTY_DISABLED, SUBJECT_MESSAGE_BOARD);
        }
        // 前台只允许查看已审核且公开的留言（MessageBoard#isDisplayable）
        List<MessageBoard> list = messageBoardService.selectList(MessageBoardArgs.of().siteId(site.getId())
                .status(List.of(MessageBoard.STATUS_AUDITED)).isOpen(true), 0, limit);
        List<Candidate> candidates = list.stream()
                .filter(MessageBoard::isDisplayable)
                .map(item -> new Candidate(SUBJECT_MESSAGE_BOARD, item.getTitle(),
                        sitePath(site, "/message-board/" + item.getId()), String.valueOf(item.getId()),
                        template, null))
                .toList();
        return new Result(candidates, candidates.isEmpty() ? EMPTY_NO_CONTENT : null, SUBJECT_MESSAGE_BOARD);
    }

    private Result user(Site site, String template) {
        User viewer = Contexts.findCurrentUser();
        if (viewer == null || viewer.isCancelled()) {
            return new Result(List.of(), EMPTY_NO_CONTENT, SUBJECT_USER);
        }
        return new Result(List.of(new Candidate(SUBJECT_USER, viewer.getUsername(),
                sitePath(site, "/users/" + viewer.getId()), String.valueOf(viewer.getId()), template, null)),
                null, null);
    }
}
