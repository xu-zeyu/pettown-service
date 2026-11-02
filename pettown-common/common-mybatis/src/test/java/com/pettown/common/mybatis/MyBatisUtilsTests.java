package com.pettown.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pettown.common.mybatis.mapper.BaseMapperX;
import com.pettown.common.mybatis.utils.MyBatisUtils;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisUtilsTests {
    @Test
    void buildsDefaultAndSortedPages() {
        Page<Object> page = MyBatisUtils.buildPage(new PageParam(),
                List.of(new SortingField("createdTime", SortingField.ORDER_DESC)));
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.orders()).singleElement().satisfies(order -> {
            assertThat(order.getColumn()).isEqualTo("created_time");
            assertThat(order.isAsc()).isFalse();
        });
    }

    @Test
    void acceptsUnpagedSizeAndValidatesBounds() {
        PageParam param = new PageParam().setSize(PageParam.PAGE_SIZE_NONE);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(param)).isEmpty();
            assertThat(factory.getValidator().validate(new PageParam().setSize(0))).isNotEmpty();
            assertThat(factory.getValidator().validate(new PageParam().setSize(1001))).isNotEmpty();
            assertThat(factory.getValidator().validate(new PageParam().setSize(null))).isNotEmpty();
        }
        Page<Object> page = MyBatisUtils.buildPage(param);
        assertThat(page.getSize()).isEqualTo(-1);
        assertThat(page.searchCount()).isFalse();
        assertThatThrownBy(() -> MyBatisUtils.buildPage(new PageParam().setPage(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MyBatisUtils.buildPage(new PageParam().setSize(-2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSqlExpressionsAndInvalidSortOrders() {
        assertThatThrownBy(() -> MyBatisUtils.buildPage(new PageParam(),
                List.of(new SortingField("id desc; drop table pet", "asc"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MyBatisUtils.buildPage(new PageParam(),
                List.of(new SortingField("id", "invalid"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void optionalLookupPropagatesDatabaseFailures() {
        BaseMapperX<Object> mapper = mock(BaseMapperX.class, CALLS_REAL_METHODS);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).selectById(1L);
        assertThatThrownBy(() -> mapper.selectByIdOpt(1L))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }
}
