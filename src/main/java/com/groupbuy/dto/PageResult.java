package com.groupbuy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页统一返回体。
 *
 * <p>约定：所有分页接口的 data 都用它包一层，字段固定为 total / pages / list，
 * 前端不用为每个接口单独适配。见 docs/api.md「通用约定」。</p>
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> of(long total, long pageSize, List<T> list) {
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(total, pages, list == null ? Collections.emptyList() : list);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, 0L, Collections.emptyList());
    }
}
