package com.ujcms.cms.core.aop.enums;

/**
 * 操作日志类型
 *
 * @author PONY
 */
public enum OperationType {
    /**
     * 新增
     */
    CREATE(1),
    /**
     * 修改
     */
    UPDATE(2),
    /**
     * 删除
     */
    DELETE(3),
    /**
     * 其它
     */
    OTHER(0);

    private Short type;

    OperationType(int type) {
        this.type = (short) type;
    }

    public Short getType() {
        return type;
    }
}
