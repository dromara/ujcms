package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ujcms.cms.core.domain.base.ChannelExtBase;

import java.io.Serializable;

/**
 * 栏目扩展数据 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class ChannelExt extends ChannelExtBase implements Serializable {
}