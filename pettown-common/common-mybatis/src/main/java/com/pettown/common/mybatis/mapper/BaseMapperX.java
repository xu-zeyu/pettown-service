package com.pettown.common.mybatis.mapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.SortablePageParam;
import com.pettown.common.mybatis.SortingField;
import com.pettown.common.mybatis.utils.MyBatisUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Param;

/**
 * 在 MyBatis Plus 的 BaseMapper 的基础上拓展，提供更多的能力
 * 1. {@link BaseMapper} 为 MyBatis Plus 的基础接口，提供基础的 CRUD 能力
 * 2. {@link MPJBaseMapper} 为 MyBatis Plus Join 的基础接口，提供连表 Join 能力
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    default IPage<T> selectPage(SortablePageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, pageParam.getSortingFields(), queryWrapper);
    }

    default IPage<T> selectPage(PageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, null, queryWrapper);
    }

    default IPage<T> selectPage(PageParam pageParam, Collection<SortingField> sortingFields, @Param("ew") Wrapper<T> queryWrapper) {
        IPage<T> mpPage = MyBatisUtils.buildPage(pageParam, sortingFields);
        selectPage(mpPage, queryWrapper);
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getSize())) {
            mpPage.setTotal(mpPage.getRecords().size());
        }
        return mpPage;
    }

    default <D> IPage<D> selectJoinPage(PageParam pageParam, Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        return selectJoinPage(pageParam, clazz, (MPJBaseJoin<T>) lambdaWrapper);
    }

    default <DTO> IPage<DTO> selectJoinPage(PageParam pageParam, Class<DTO> resultTypeClass, MPJBaseJoin<T> joinQueryWrapper) {
        Collection<SortingField> sortingFields = pageParam instanceof SortablePageParam sortable
                ? sortable.getSortingFields() : null;
        IPage<DTO> mpPage = MyBatisUtils.buildPage(pageParam, sortingFields);
        selectJoinPage(mpPage, resultTypeClass, joinQueryWrapper);
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getSize())) {
            mpPage.setTotal(mpPage.getRecords().size());
        }
        return mpPage;
    }

    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }
    default Optional<T> selectOneOpt(Wrapper<T> queryWrapper) {
        return Optional.ofNullable(selectOne(queryWrapper));
    }

    default Optional<T> selectByIdOpt(Serializable id) {
        return Optional.ofNullable(selectById(id));
    }

    /**
     * 获取满足条件的第 1 条记录
     *
     * 目的：解决并发场景下，插入多条记录后，使用 selectOne 会报错的问题
     *
     * @param field 字段名
     * @param value 字段值
     * @return 实体
     */
    default T selectFirstOne(SFunction<T, ?> field, Object value) {
        List<T> list = selectList(new Page<T>(1, 1, false), new LambdaQueryWrapper<T>().eq(field, value));
        return list.isEmpty() ? null : list.get(0);
    }

    default T selectFirstOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        List<T> list = selectList(new Page<T>(1, 1, false), new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
        return list.isEmpty() ? null : list.get(0);
    }

    default T selectFirstOne(SFunction<T,?> field1, Object value1, SFunction<T,?> field2, Object value2,
                             SFunction<T,?> field3, Object value3) {
        List<T> list = selectList(new Page<T>(1, 1, false), new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
        return list.isEmpty() ? null : list.get(0);
    }


    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     */
    default Boolean insertBatch(Collection<T> entities) {
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入，适合大量数据插入
     *
     * @param entities 实体们
     * @param size     插入数量 Db.saveBatch 默认为 1000
     */
    default Boolean insertBatch(Collection<T> entities, int size) {
        return Db.saveBatch(entities, size);
    }

    default Boolean updateBatch(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }

    default Boolean updateBatch(Collection<T> entities, int size) {
        return Db.updateBatchById(entities, size);
    }

    default int delete(String field, String value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }
}
