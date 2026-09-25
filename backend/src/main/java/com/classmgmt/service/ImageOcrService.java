package com.classmgmt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 图片 OCR：优先 Windows 系统 OCR（中文截图效果更好），失败则回退 Tesseract。
 */
@Service
public class ImageOcrService {

    @Value("${app.tesseract.executable:C:\\Program Files\\Tesseract-OCR\\tesseract.exe}")
    private String tesseractExecutable;

    @Value("${app.tesseract.tessdata:}")
    private String tessdataDir;

    @Value("${app.tesseract.lang:chi_sim+eng}")
    private String lang;

    @Value("${app.tesseract.psm:6}")
    private String psm;

    @Value("${app.ocr.prefer-windows:true}")
    private boolean preferWindowsOcr;

    public String ocrToText(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("roster-ocr-", extensionOf(image));
            image.transferTo(tmp);

            if (preferWindowsOcr) {
                try {
                    String win = windowsOcr(tmp);
                    if (win != null && !win.isBlank()) {
                        return win;
                    }
                } catch (Exception ignored) {
                    // fallback tesseract
                }
            }
            return tesseractOcr(tmp);
        } catch (IOException e) {
            throw new IllegalStateException("无法调用 OCR：" + e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String extensionOf(MultipartFile image) {
        String name = image.getOriginalFilename() == null ? "" : image.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (name.endsWith(".bmp")) {
            return ".bmp";
        }
        if (name.endsWith(".webp")) {
            return ".webp";
        }
        if (name.endsWith(".gif")) {
            return ".gif";
        }
        if (name.endsWith(".tif") || name.endsWith(".tiff")) {
            return ".tiff";
        }
        return ".png";
    }

    private String windowsOcr(Path image) throws Exception {
        Path script = Files.createTempFile("win-ocr-", ".ps1");
        try {
            try (InputStream in = new ClassPathResource("win-ocr.ps1").getInputStream()) {
                Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(),
                    "-ImagePath", image.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    err.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(40, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Windows OCR 超时");
            }
            String text = out.toString();
            if (process.exitValue() == 0 && !text.isBlank()) {
                return text;
            }
            throw new IllegalStateException("Windows OCR 失败: " + err);
        } finally {
            try {
                Files.deleteIfExists(script);
            } catch (IOException ignored) {
            }
        }
    }

    private String tesseractOcr(Path tmp) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    tesseractExecutable,
                    tmp.toAbsolutePath().toString(),
                    "stdout",
                    "-l", lang,
                    "--psm", psm
            );
            if (tessdataDir != null && !tessdataDir.isBlank()) {
                pb.environment().put("TESSDATA_PREFIX", tessdataDir);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("OCR 超时（45s）");
            }
            int code = process.exitValue();
            String text = out.toString();
            if (code != 0 && text.isBlank()) {
                throw new IllegalStateException("OCR 失败，exit=" + code + "；请检查 Tesseract 与中文语言包");
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("无法调用 OCR：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR 被中断", e);
        }
    }
}
