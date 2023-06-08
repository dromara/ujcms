package com.ujcms.commons.ip;

/**
 * @author PONY
 */
public class Region {
    /**
     * 内网
     */
    public static final String LAN = "LAN";
    /**
     * 未知
     */
    public static final String UNKNOWN = "UNKNOWN";

    public Region() {
    }

    public Region(String country, String province, String city, String provider) {
        this.country = country;
        this.province = province;
        this.city = city;
        this.provider = provider;
    }

    private String country = UNKNOWN;
    private String province = UNKNOWN;
    private String city = UNKNOWN;
    private String provider = UNKNOWN;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
