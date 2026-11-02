package com.pettown.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.yulichang.interfaces.MPJBaseJoin;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.pet.service.PetService;
import com.pettown.business.pet.service.impl.PetServiceImpl;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.business.user.service.UserAuthService;
import com.pettown.business.user.service.impl.UserAuthServiceImpl;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.mybatis.SortablePageParam;
import com.pettown.common.mybatis.SortingField;
import com.pettown.common.mybatis.config.MyMetaObjectHandler;
import com.pettown.common.mybatis.config.MybatisPlusConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = PersistenceTests.TestApplication.class, properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=always", "pettown.user.enabled=true"
})
@Transactional
class PersistenceTests {
    @Autowired private PetMapper petMapper;
    @Autowired private PetService petService;
    @Autowired private UserMapper userMapper;
    @Autowired private UserAuthService userAuthService;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan({"com.pettown.business.pet.mapper", "com.pettown.business.user.mapper"})
    @Import({MybatisPlusConfig.class, MyMetaObjectHandler.class,
            PetServiceImpl.class, UserAuthServiceImpl.class})
    static class TestApplication {
    }

    @Test
    void persistsPetsAndFillsAuditTimes() {
        Pet pet = petService.createPet(" 豆豆 ", " 猫 ");
        assertThat(pet.getId()).isPositive();
        Pet stored = petService.getPet(pet.getId());
        assertThat(stored.getName()).isEqualTo("豆豆");
        assertThat(stored.getCreatedTime()).isNotNull();
        assertThat(stored.getUpdatedTime()).isNotNull();

        Pet update = stored;
        update.setName("团团");
        update.setUpdatedTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        petMapper.updateById(update);
        Pet updated = petMapper.selectById(stored.getId());
        assertThat(updated.getName()).isEqualTo("团团");
        assertThat(updated.getCreatedTime()).isEqualTo(stored.getCreatedTime());
        assertThat(updated.getUpdatedTime()).isAfter(LocalDateTime.of(2000, 1, 1, 0, 0));
    }

    @Test
    void pagesAndSortsInDatabaseIncludingUnpagedQueries() {
        petService.createPet("A", "猫");
        petService.createPet("B", "猫");
        petService.createPet("C", "猫");
        SortablePageParam param = new SortablePageParam();
        param.setSortingFields(List.of(new SortingField("name", "desc")));
        param.setPage(2).setSize(1);
        IPage<Pet> page = petMapper.selectPage(param, new LambdaQueryWrapper<>());
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).extracting(Pet::getName).containsExactly("B");
        assertThat(PageResult.from(page).getTotal()).isEqualTo(3);

        param.setSize(PageParam.PAGE_SIZE_NONE);
        IPage<Pet> all = petMapper.selectPage(param, new LambdaQueryWrapper<>());
        assertThat(all.getTotal()).isEqualTo(3);
        assertThat(all.getRecords()).extracting(Pet::getName).containsExactly("C", "B", "A");
        assertThat(petMapper.selectList(Pet::getId, List.of())).isEmpty();
        assertThat(petMapper.selectFirstOne(Pet::getName, "B").getName()).isEqualTo("B");
    }

    @Test
    void injectsJoinQueriesAndKeepsUnpagedTotals() {
        petService.createPet("A", "猫");
        petService.createPet("B", "猫");
        // Join the same table by ID to exercise actual JOIN SQL without adding a business table.
        MPJLambdaWrapper<Pet> join = new MPJLambdaWrapper<Pet>().selectAll(Pet.class)
                .leftJoin(Pet.class, "other", Pet::getId, Pet::getId);
        IPage<Pet> page = petMapper.selectJoinPage(new PageParam().setSize(1), Pet.class, join);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(1);
        IPage<Pet> all = petMapper.selectJoinPage(new PageParam().setSize(-1), Pet.class, join);
        assertThat(all.getTotal()).isEqualTo(2);
        assertThat(all.getRecords()).hasSize(2);
        MPJBaseJoin<Pet> genericJoin = join;
        assertThat(petMapper.selectJoinPage(new PageParam().setSize(-1), Pet.class, genericJoin).getTotal())
                .isEqualTo(2);
    }

    @Test
    void registersAndAuthenticatesWithStringIdsAndUniquePhones() {
        User registered = userAuthService.loginOrRegister("13800138000", "strongPass123");
        assertThat(registered.getId()).matches("pettown[0-9a-f]{24}");
        assertThat(registered.getPasswordHash()).startsWith("$2").isNotEqualTo("strongPass123");
        assertThat(userAuthService.getById(registered.getId()).getPhone()).isEqualTo("13800138000");
        assertThat(userAuthService.loginOrRegister("13800138000", "strongPass123").getId())
                .isEqualTo(registered.getId());
        assertThatThrownBy(() -> userAuthService.loginOrRegister("13800138000", "wrongPass123"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> userMapper.insert(new User("pettown0123456789abcdef01234567",
                "13800138000", "unused"))).isInstanceOf(DuplicateKeyException.class);
        assertThat(userMapper.selectCount()).isEqualTo(1);
    }
}
