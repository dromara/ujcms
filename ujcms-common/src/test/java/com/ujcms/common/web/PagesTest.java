package com.ujcms.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存列表切页：页码从 1 开始，越界页空内容但总数不变
 */
class PagesTest {
    private static final List<Integer> LIST = IntStream.rangeClosed(1, 25).boxed().toList();

    @Test
    void slicesTheRequestedPage() {
        Page<Integer> page = Pages.of(LIST, 2, 10);
        assertEquals(IntStream.rangeClosed(11, 20).boxed().toList(), page.getContent());
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        // Spring Data 页码从 0 开始
        assertEquals(1, page.getNumber());
        assertEquals(10, page.getSize());
    }

    @Test
    void lastPageIsShort() {
        Page<Integer> page = Pages.of(LIST, 3, 10);
        assertEquals(List.of(21, 22, 23, 24, 25), page.getContent());
        assertTrue(page.isLast());
    }

    @Test
    void pageBeyondEndIsEmptyButKeepsTotal() {
        Page<Integer> page = Pages.of(LIST, 9, 10);
        assertTrue(page.getContent().isEmpty());
        assertEquals(25, page.getTotalElements());
    }

    @Test
    void emptyListFirstPage() {
        Page<Integer> page = Pages.of(Collections.emptyList(), 1, 10);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void hugePageNumberDoesNotOverflow() {
        Page<Integer> page = Pages.of(LIST, Integer.MAX_VALUE, 1000);
        assertTrue(page.getContent().isEmpty());
        assertEquals(25, page.getTotalElements());
    }

    @Test
    void rejectsNonPositiveArguments() {
        assertThrows(IllegalArgumentException.class, () -> Pages.of(LIST, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> Pages.of(LIST, 1, 0));
    }
}
