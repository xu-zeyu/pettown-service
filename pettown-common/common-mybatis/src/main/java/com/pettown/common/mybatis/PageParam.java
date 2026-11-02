package com.pettown.common.mybatis;

import java.io.Serializable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PageParam implements Serializable {

    private static final Integer PAGE_NO = 1;
    private static final Integer PAGE_SIZE = 10;

    /**
     * 每页条数 - 不分页
     *
     * 例如说，导出接口，可以设置 {@link #size} 为 -1 不分页，查询所有数据。
     */
    public static final Integer PAGE_SIZE_NONE = -1;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    private Integer page = PAGE_NO;

    @NotNull(message = "每页条数不能为空")
    private Integer size = PAGE_SIZE;

    @AssertTrue(message = "每页条数须为 -1 或 1 至 1000")
    public boolean isSizeValid() {
        return size == null || PAGE_SIZE_NONE.equals(size) || (size >= 1 && size <= 1000);
    }

}
