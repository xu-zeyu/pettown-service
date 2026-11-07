package com.pettown.admin.verification.service;

import com.pettown.admin.verification.vo.CaptchaResponse;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 登录图形验证码：4 位去易混字符，PNG Base64 签发，5 分钟有效、一次性消费。 */
@Service
public class AdminCaptchaService {
    static final String CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final CaptchaStore store;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AdminCaptchaService(CaptchaStore store) {
        this.store = store;
    }

    public CaptchaResponse issue() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        String captchaId = java.util.UUID.randomUUID().toString();
        store.save(captchaId, code.toString());
        return new CaptchaResponse(captchaId, Base64.getEncoder().encodeToString(render(code.toString())));
    }

    /** 一次性校验：无论成功与否，消费后该 ID 立即失效（防同票多次猜测）。 */
    public boolean consume(String captchaId, String code) {
        String expected = store.pop(captchaId);
        return expected != null && code != null && expected.equalsIgnoreCase(code.trim());
    }

    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 6; i++) {
                graphics.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
                graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(20 + random.nextInt(100), 20 + random.nextInt(100), 20 + random.nextInt(100)));
                AffineTransform original = graphics.getTransform();
                graphics.rotate(Math.toRadians(-20 + random.nextInt(40)), 20 + i * 26, 26);
                graphics.drawString(String.valueOf(code.charAt(i)), 12 + i * 26, 30);
                graphics.setTransform(original);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("验证码图片生成失败", exception);
        }
    }
}
