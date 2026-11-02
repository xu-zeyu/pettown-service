package com.pettown.business.user.mapper;

import com.pettown.business.user.entity.User;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "pettown.user", name = "enabled", havingValue = "true")
public class JdbcUserMapper implements UserMapper {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return jdbcTemplate.query("SELECT id, phone, password_hash FROM user_account WHERE phone = ?",
                (rs, rowNum) -> new User(rs.getLong("id"), rs.getString("phone"), rs.getString("password_hash")),
                phone).stream().findFirst();
    }

    @Override
    public User insert(String phone, String passwordHash) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO user_account (phone, password_hash) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, phone);
            statement.setString(2, passwordHash);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建用户后未返回 ID");
        }
        return new User(id.longValue(), phone, passwordHash);
    }
}
