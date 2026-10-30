package com.pettown.common.mybatis.pagination;

public record PageParam(long page, long size) {
    public PageParam {
        if (page < 1 || size < 1 || size > 1000) {
            throw new IllegalArgumentException("页码或每页条数不合法");
        }
    }
}
