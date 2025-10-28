package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.ext.domain.base.VoteOptionBase;

/**
 * 投票选项 实体类
 *
 * @author PONY
 */
public class VoteOption extends VoteOptionBase {

    public double getPercent() {
        int total = getVote().getTotal();
        if (total > 0) {
            return (double) getCount() * 100 / total;
        }
        return 0;
    }

    @JsonIgnore
    private Vote vote = new Vote();

    public Vote getVote() {
        return vote;
    }

    public void setVote(Vote vote) {
        this.vote = vote;
    }
}