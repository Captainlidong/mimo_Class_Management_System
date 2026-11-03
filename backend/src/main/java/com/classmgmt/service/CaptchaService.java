package com.classmgmt.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：内存态一次性凭证，3 分钟过期。
 * 字符集剔除 0/O/1/I 等易混字符，校验忽略大小写、常量时间比较。
 */
@Service
public class CaptchaService {

    private static final Duration TTL = Duration.ofMinutes(3);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_PENDING = 2000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> pending = new ConcurrentHashMap<>();

    public record Captcha(String captchaId, String image) {
    }

    private record Entry(String code, Instant expiresAt) {
    }

    public Captcha generate() {
        purge();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        pending.put(id, new Entry(sb.toString(), Instant.now().plus(TTL)));
        String image;
        try {
            image = "data:image/png;base64," + Base64.getEncoder().encodeToString(draw(sb.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码失败", e);
        }
        return new Captcha(id, image);
    }

    /** 校验并销毁（一次性）；验证码错误不参与登录锁定计数。 */
    public boolean verify(String id, String input) {
        if (id == null || id.isBlank() || input == null) {
            return false;
        }
        Entry entry = pending.remove(id.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return false;
        }
        String provided = input.trim().toUpperCase(Locale.ROOT);
        return MessageDigest.isEqual(
                entry.code().getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void purge() {
        pending.values().removeIf(e -> e.expiresAt().isBefore(Instant.now()));
        if (pending.size() > MAX_PENDING) {
            pending.clear();
        }
    }

    private byte[] draw(String code) throws IOException {
        int w = 132;
        int h = 44;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(252, 242, 246));
        g.fillRect(0, 0, w, h);

        for (int i = 0; i < 5; i++) {
            g.setColor(pastel());
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        for (int i = 0; i < code.length(); i++) {
            java.awt.geom.AffineTransform old = g.getTransform();
            int x = 10 + i * 30;
            double theta = (random.nextDouble() - 0.5) * 0.55;
            g.rotate(theta, x + 14, h / 2.0);
            g.setColor(ink());
            g.drawString(String.valueOf(code.charAt(i)), x, 32);
            g.setTransform(old);
        }

        for (int i = 0; i < 50; i++) {
            img.setRGB(random.nextInt(w), random.nextInt(h), pastel().getRGB());
        }
        g.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private Color pastel() {
        return new Color(200 + random.nextInt(55), 170 + random.nextInt(70), 190 + random.nextInt(65));
    }

    private Color ink() {
        Color[] inks = {
                new Color(190, 24, 93),
                new Color(109, 40, 217),
                new Color(13, 100, 145),
                new Color(120, 53, 15)
        };
        return inks[random.nextInt(inks.length)];
    }
}
