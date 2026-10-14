package com.ujcms.cms.ext.service;

import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.mapper.MessageBoardMapper;
import com.ujcms.cms.ext.mapper.MessageBoardTypeMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 留言类型删除契约（docs/spec/console-master-detail R4）：留言必须有类型，且是访客提交的数据——类型下还有留言即拒绝，
 * 给出带类型名的提示；此前是裸删，撞上外键后用户看到的是原始的数据库错误。
 */
class MessageBoardTypeServiceTest {
    private MessageBoardMapper messageBoardMapper;
    private MessageBoardTypeMapper mapper;
    private MessageBoardTypeService service;

    @BeforeEach
    void setUp() {
        messageBoardMapper = mock(MessageBoardMapper.class);
        mapper = mock(MessageBoardTypeMapper.class);
        service = new MessageBoardTypeService(messageBoardMapper, mapper, mock(SnowflakeSequence.class));
    }

    @Test
    void deleteIsRefusedWhileMessagesUseTheType() {
        MessageBoardType type = new MessageBoardType();
        type.setId(5L);
        type.setName("咨询");
        when(mapper.select(5L)).thenReturn(type);
        when(messageBoardMapper.existsByTypeId(5L)).thenReturn(1);

        LogicException e = assertThrows(LogicException.class, () -> service.delete(5L));
        assertEquals("error.messageBoardType.inUse", e.getCode());
        verify(mapper, never()).delete(5L);
    }

    @Test
    void deleteOfAnUnusedTypeGoesThrough() {
        when(messageBoardMapper.existsByTypeId(5L)).thenReturn(0);
        when(mapper.delete(5L)).thenReturn(1);
        assertEquals(1, service.delete(5L));
    }
}
