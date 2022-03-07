package com.ofwise.util.query;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serializable;

/**
 * Spring Data 的 offset limit 请求。
 *
 * @author PONY
 * @see org.springframework.data.domain.PageRequest
 * @see org.springframework.data.domain.Pageable
 */
public class OffsetLimitRequest implements Pageable, Serializable {
    private final long offset;
    private final int limit;
    private final Sort sort;

    /**
     * Creates a new {@link OffsetLimitRequest} with sort parameters applied.
     *
     * @param offset zero-based offset.
     * @param limit  the size of the elements to be returned, must be greater than 0.
     * @param sort   can be {@literal null}.
     */
    protected OffsetLimitRequest(long offset, int limit, Sort sort) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset index must not be less than zero!");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must not be less than one!");
        }
        this.limit = limit;
        this.offset = offset;
        this.sort = sort;
    }

    /**
     * Creates a new unsorted {@link OffsetLimitRequest}.
     *
     * @param offset zero-based offset.
     * @param limit  the size of the elements to be returned, must be greater than 0.
     */
    public static OffsetLimitRequest of(long offset, int limit) {
        return of(offset, limit, Sort.unsorted());
    }

    /**
     * Creates a new {@link OffsetLimitRequest} with sort parameters applied.
     *
     * @param offset zero-based offset.
     * @param limit  the size of the elements to be returned, must be greater than 0.
     * @param sort   must not be {@literal null}, use {@link Sort#unsorted()} instead.
     */
    public static OffsetLimitRequest of(long offset, int limit, Sort sort) {
        return new OffsetLimitRequest(offset, limit, sort);
    }

    /**
     * Creates a new {@link OffsetLimitRequest} with sort direction and properties applied.
     *
     * @param offset     zero-based offset.
     * @param limit      the size of the elements to be returned, must be greater than 0.
     * @param direction  must not be {@literal null}.
     * @param properties must not be {@literal null}.
     */
    public static OffsetLimitRequest of(long offset, int limit, Sort.Direction direction, String... properties) {
        return of(offset, limit, Sort.by(direction, properties));
    }

    /**
     * Creates a new {@link OffsetLimitRequest} for the first page (page number {@code 0}) given {@code limit} .
     *
     * @param limit the size of the elements to be returned, must be greater than 0.
     * @return a new {@link PageRequest}.
     */
    public static OffsetLimitRequest ofSize(int limit) {
        return OffsetLimitRequest.of(0, limit);
    }

    /**
     * Creates a new {@link OffsetLimitRequest} with sort parameters applied.
     *
     * @param offset zero-based offset.
     * @param limit  the size of the elements to be returned.
     */
    public OffsetLimitRequest(long offset, int limit) {
        this(offset, limit, Sort.unsorted());
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimitRequest(getOffset() + getPageSize(), getPageSize(), getSort());
    }

    public OffsetLimitRequest previous() {
        return hasPrevious() ? new OffsetLimitRequest(getOffset() - getPageSize(), getPageSize(), getSort()) : this;
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? previous() : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimitRequest(0, getPageSize(), getSort());
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetLimitRequest(getPageSize() * (long) pageNumber, getPageSize(), getSort());
    }

    @Override
    public boolean hasPrevious() {
        return offset > limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OffsetLimitRequest)) {
            return false;
        }

        OffsetLimitRequest that = (OffsetLimitRequest) o;

        return new EqualsBuilder()
                .append(limit, that.limit)
                .append(offset, that.offset)
                .append(sort, that.sort)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(limit).append(offset).append(sort).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("limit", limit)
                .append("offset", offset)
                .append("sort", sort)
                .toString();
    }
}
