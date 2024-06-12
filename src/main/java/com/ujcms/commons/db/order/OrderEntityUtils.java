package com.ujcms.commons.db.order;

/**
 * 排序工具类
 *
 * @author PONY
 */
public class OrderEntityUtils {
    public static void move(OrderEntityMapper mapper, Long fromId, Long toId) {
        OrderEntity fromBean = mapper.select(fromId);
        OrderEntity toBean = mapper.select(toId);
        if (fromBean == null || toBean == null) {
            return;
        }
        long fromOrder = fromBean.getOrder();
        long toOrder = toBean.getOrder();

        if (fromOrder > toOrder) {
            // 往上移动
            mapper.moveUp(fromOrder, toOrder);
            mapper.updateOrder(fromId, toOrder);
        } else if (fromOrder < toOrder) {
            // 往下移动
            mapper.moveDown(fromOrder, toOrder);
            mapper.updateOrder(fromId, toOrder);
        } else {
            // order相同。属于意外情况，一般情况下不会出现
            if (fromId > toId) {
                // 往上移动（id 默认从小到大排）
                mapper.updateOrder(toId, toOrder + 1);
            } else {
                // 往下移动
                mapper.updateOrder(fromId, fromOrder + 1);
            }
        }
    }

    private OrderEntityUtils() {
        throw new IllegalStateException("Utility class");
    }
}
