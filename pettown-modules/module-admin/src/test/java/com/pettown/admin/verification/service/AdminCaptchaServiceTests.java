package com.pettown.admin.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pettown.admin.verification.vo.CaptchaResponse;
import com.pettown.common.exception.BusinessException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminCaptchaServiceTests {
    private FakeStore store;
    private AdminCaptchaService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        service = new AdminCaptchaService(store);
    }

    @Test
    void issuesDecodablePngWithFourCharCharsetCode() {
        CaptchaResponse response = service.issue();
        assertThat(response.captchaId()).isNotBlank();
        String code = store.codes.get(response.captchaId());
        assertThat(code).hasSize(AdminCaptchaService.CODE_LENGTH);
        assertThat(code.chars().allMatch(c -> AdminCaptchaService.CHARSET.indexOf(c) >= 0)).isTrue();
        byte[] image = Base64.getDecoder().decode(response.imageBase64());
        assertThat(image[0]).isEqualTo((byte) 137);
        assertThat(image[1]).isEqualTo((byte) 80);
        assertThat(image[2]).isEqualTo((byte) 78);
        assertThat(image[3]).isEqualTo((byte) 71);
    }

    @Test
    void consumeIsOneTimeAndCaseInsensitive() {
        String id = service.issue().captchaId();
        String code = store.codes.get(id);
        assertThat(service.consume(id, code.toLowerCase())).isTrue();
        assertThat(service.consume(id, code)).isFalse();
    }

    @Test
    void wrongGuessAlsoConsumesTheTicket() {
        String id = service.issue().captchaId();
        String code = store.codes.get(id);
        assertThat(service.consume(id, "WRONG")).isFalse();
        assertThat(service.consume(id, code)).isFalse();
    }

    @Test
    void rejectsUnknownIdAndNullCode() {
        String id = service.issue().captchaId();
        String code = store.codes.get(id);
        assertThat(service.consume("missing-id", code)).isFalse();
        assertThat(service.consume(id, null)).isFalse();
        // 空码也视为一次校验尝试并消费票据，防同票多次猜测。
        assertThat(service.consume(id, code)).isFalse();
    }

    static class FakeStore implements CaptchaStore {
        final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void save(String captchaId, String code) {
            codes.put(captchaId, code);
        }

        @Override
        public String pop(String captchaId) {
            return codes.remove(captchaId);
        }
    }
}
