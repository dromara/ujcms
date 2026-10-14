package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.support.FirstInitSeeder;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.cms.ext.service.MessageBoardTypeService;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 首装扩展模块演示数据：示例留言（含回复）。
 *
 * <p>留言属虚构用户内容，无种子家族——保留在安装完成 seeder 里（install-flow D13）。
 * 原先的示例投票已迁入默认主题种子（P2）：投票家族（E4b）以来源三元组创建，
 * 「移除演示数据」可移除、跨站安装可携带。演示表单提交属虚构用户数据（含悬空的
 * 工作流实例引用），已随脚本清理删除、不再重建。
 *
 * @author PONY
 */
@Component
public class ExtDemoSeeder implements FirstInitSeeder {
    private final MessageBoardService messageBoardService;
    private final MessageBoardTypeService messageBoardTypeService;

    public ExtDemoSeeder(MessageBoardService messageBoardService,
                         MessageBoardTypeService messageBoardTypeService) {
        this.messageBoardService = messageBoardService;
        this.messageBoardTypeService = messageBoardTypeService;
    }

    @Override
    public void seed(Long siteId, Long userId) {
        List<MessageBoardType> types = messageBoardTypeService.selectList(
                MessageBoardTypeArgs.of().siteId(siteId), 0, 1);
        if (types.isEmpty()) {
            return;
        }
        MessageBoard bean = new MessageBoard();
        bean.setSiteId(siteId);
        bean.setTypeId(types.get(0).getId());
        bean.setTitle("关于版本发布");
        bean.setText("请问新版本什么时候发布？");
        bean.setStatus(MessageBoard.STATUS_AUDITED);
        // 留言者为匿名用户（用户 0），与前台匿名提交一致
        messageBoardService.insert(bean, 0L, "127.0.0.1");
        bean.setReplyText("<p>您好，根据技术部反馈的信息，新版本将在近期发布。敬请关注，谢谢！</p>");
        bean.setReplyUserId(userId);
        bean.setReplyDate(OffsetDateTime.now());
        messageBoardService.update(bean);
    }
}
