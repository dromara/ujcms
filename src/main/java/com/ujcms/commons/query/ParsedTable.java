package com.ujcms.commons.query;

import static com.ujcms.commons.query.QueryUtils.camelToUnderscore;

/**
 * 解析表连接信息
 * <p>
 * 五种情况
 * <li> many-to-one: user                   -> user, user_, t.user_id_, user.id_
 * <li> many-to-one: editUser@user          -> user, editUser_, t.edit_user_id_, editUser_.id_
 * <li> one-to-one : @userExt               -> user_ext, userExt_, t.id_, userExt_.id_
 * <li> one-to-many: UserRole               -> user_role, userRole_, t.id_, userRole_.user_id_ (user_id_ 通过leftTable推断)
 * <li> one-to-many: descendant@OrgTree     -> org_tree, orgTree_, t.id_, orgTree_.descendant_id_
 * <li> one-to-many: org@OrgTree@descendant -> org_tree, orgTree_, t.org_id_, orgTree_.descendant_id_
 *
 * @author PONY
 */
class ParsedTable {
    private String tablePart;
    private String leftColumn = "";
    private String rightColumn = "";
    private String rightAlias;
    private String rightTable;
    private String leftId = "";
    private String rightId = "";
    private boolean one2one = false;
    private boolean one2many = false;

    public ParsedTable(String tablePart) {
        this.tablePart = tablePart;
        this.rightAlias = tablePart;
        this.rightTable = tablePart;
    }

    /**
     * 处理第一个 @
     */
    public void firstAt() {
        int atIndex = tablePart.indexOf("@");
        if (atIndex >= 0) {
            // editUser
            leftColumn = tablePart.substring(0, atIndex);
            rightAlias = leftColumn;
            // user
            // questionExt
            // UserExt
            rightTable = tablePart.substring(atIndex + 1);
            // 处理 one2one 情形 @userExt @questionExt。此时rightAlias为空串
            if (leftColumn.isEmpty()) {
                one2one = true;
                rightAlias = rightTable;
            }
        }
    }

    /**
     * 首字母大写，为one2many
     */
    public void upperCaseTable() {
        if (Character.isUpperCase(rightTable.charAt(0))) {
            one2many = true;
            // 首字母换回小写
            rightTable = rightTable.substring(0, 1).toLowerCase() + rightTable.substring(1);
            // one2many 双 @ 情景：org@OrgTree@descendant
            int atIndex = rightTable.indexOf("@");
            if (atIndex >= 0) {
                rightColumn = rightTable.substring(atIndex + 1);
                rightTable = rightTable.substring(0, atIndex);
            }
            rightAlias = rightTable;
        }
    }

    public void leftIdAndRightId(String leftTable) {
        // 表别名下划线结尾，以免与数据库关键字冲突
        // user_
        // editUser_
        // questionExt_
        // userExt_
        rightAlias = rightAlias + "_";
        // t_.id_
        // t_.user_id_
        // t_.edit_user_id_
        leftId = camelToUnderscore(rightAlias) + "id_";
        if (one2many || one2one) {
            leftId = "id_";
            if (!rightColumn.isEmpty()) {
                leftId = camelToUnderscore(leftColumn) + "_id_";
            }
        }
        // user_.id_
        // userExt_.user_id_
        rightId = "id_";
        if (one2many) {
            if (leftColumn.isEmpty()) {
                // origAlias为空，使用 leftTable 推断
                rightId = camelToUnderscore(leftTable) + "_id_";
            } else {
                rightId = camelToUnderscore(rightColumn.isEmpty() ? leftColumn : rightColumn) + "_id_";
            }
        }
    }

    public String getTablePart() {
        return tablePart;
    }

    public void setTablePart(String tablePart) {
        this.tablePart = tablePart;
    }

    public String getLeftColumn() {
        return leftColumn;
    }

    public void setLeftColumn(String leftColumn) {
        this.leftColumn = leftColumn;
    }

    public String getRightColumn() {
        return rightColumn;
    }

    public void setRightColumn(String rightColumn) {
        this.rightColumn = rightColumn;
    }

    public String getRightAlias() {
        return rightAlias;
    }

    public void setRightAlias(String rightAlias) {
        this.rightAlias = rightAlias;
    }

    public String getRightTable() {
        return rightTable;
    }

    public void setRightTable(String rightTable) {
        this.rightTable = rightTable;
    }

    public String getLeftId() {
        return leftId;
    }

    public void setLeftId(String leftId) {
        this.leftId = leftId;
    }

    public String getRightId() {
        return rightId;
    }

    public void setRightId(String rightId) {
        this.rightId = rightId;
    }
}

