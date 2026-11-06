package com.pettown.miniapp.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.file.service.FileService;
import com.pettown.miniapp.file.config.MiniappFileProperties;
import com.pettown.miniapp.file.service.MiniappFileService;
import com.pettown.miniapp.file.service.UploadQuota;
import com.pettown.miniapp.user.vo.UserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class UserProfileServiceTests {
    private static final String USER_ID = "user-profile-0001";
    private static final String AVATAR = "https://files.example.test/api/miniapp/files/"
            + "11111111-1111-4111-8111-111111111111.png";
    private final UserMapper users = mock(UserMapper.class);
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        MiniappFileService fileService = new MiniappFileService(
                new DefaultListableBeanFactory().getBeanProvider(FileService.class),
                mock(UploadQuota.class), new MiniappFileProperties("https://files.example.test"));
        service = new UserProfileService(users, fileService);
        User user = new User(USER_ID, "13800138041", "hash", UserSourceEnum.MINI_PROGRAM);
        when(users.selectById(USER_ID)).thenReturn(user);
    }

    @Test
    void updatesBothFieldsAndReturnsNewValues() {
        UserInfoResponse response = service.update(USER_ID, "豆豆妈", AVATAR);
        assertThat(response.nickname()).isEqualTo("豆豆妈");
        assertThat(response.avatarUrl()).isEqualTo(AVATAR);
        verify(users).updateById(any(User.class));
    }

    @Test
    void trimsNickname() {
        assertThat(service.update(USER_ID, "  豆豆妈  ", null).nickname()).isEqualTo("豆豆妈");
    }

    @Test
    void acceptsExactlyTwentyCharacters() {
        assertThat(service.update(USER_ID, "一二三四五六七八九十一二三四五六七八九十", null).nickname())
                .hasSize(20);
    }

    @Test
    void rejectsBlankOversizeAndSensitiveNickname() {
        for (String bad : new String[]{"", "   ", "一二三四五六七八九十一二三四五六七八九十一", "我是傻逼", "FUCK"}) {
            assertThatThrownBy(() -> service.update(USER_ID, bad, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("昵称");
        }
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void rejectsAvatarOutsideOwnUploadEndpoint() {
        for (String bad : new String[]{
                "https://evil.example.test/a.png",
                "https://files.example.test/api/miniapp/files/../secret",
                "https://files.example.test/api/miniapp/files/not-a-uuid.png",
                "http://evil.example.test/api/miniapp/files/11111111-1111-4111-8111-111111111111.png"}) {
            assertThatThrownBy(() -> service.update(USER_ID, null, bad))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("头像");
        }
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void rejectsUpdateWithoutAnyField() {
        assertThatThrownBy(() -> service.update(USER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能同时为空");
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void rejectsWhenUserMissing() {
        assertThatThrownBy(() -> service.update("missing-user", "豆豆妈", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }
}
