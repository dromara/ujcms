package com.ujcms.commons.db;

/**
 * @author PONY
 */
public class Constants {
    /**
     * 性别：保密
     */
    public static final short GENDER_NONE = 0;
    /**
     * 性别：男性
     */
    public static final short GENDER_MALE = 1;
    /**
     * 性别：女性
     */
    public static final short GENDER_FEMALE = 2;

    private Constants() {
        throw new IllegalStateException("Utility class");
    }
}
