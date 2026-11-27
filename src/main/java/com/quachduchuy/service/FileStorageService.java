package com.quachduchuy.service;

import com.quachduchuy.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Lưu ảnh tải lên vào thư mục trên máy chủ; trình duyệt xem qua đường dẫn /uploads/**. */
@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục ảnh " + root, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    /** Kiểm tra file là ảnh thật rồi lưu với tên ngẫu nhiên. Trả về đường dẫn dùng trong thẻ img. */
    public String store(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Ảnh tối đa 5MB");
        }
        try {
            byte[] data = file.getBytes();
            // Xác định loại ảnh theo nội dung, không tin tên file hay Content-Type do trình duyệt gửi
            String extension = detectExtension(data);
            if (extension == null) {
                throw new BusinessException("Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP");
            }
            String name = UUID.randomUUID() + extension;
            Files.write(root.resolve(name), data);
            return "/uploads/" + name;
        } catch (IOException e) {
            log.error("Lưu ảnh thất bại", e);
            throw new BusinessException("Không lưu được ảnh, vui lòng thử lại");
        }
    }

    private String detectExtension(byte[] d) {
        if (startsWith(d, 0, 0xFF, 0xD8, 0xFF)) return ".jpg";
        if (startsWith(d, 0, 0x89, 'P', 'N', 'G')) return ".png";
        if (startsWith(d, 0, 'G', 'I', 'F', '8')) return ".gif";
        if (startsWith(d, 0, 'R', 'I', 'F', 'F') && startsWith(d, 8, 'W', 'E', 'B', 'P')) return ".webp";
        return null;
    }

    private boolean startsWith(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        byte[] expected = new byte[signature.length];
        for (int i = 0; i < signature.length; i++) {
            expected[i] = (byte) signature[i];
        }
        return Arrays.equals(data, offset, offset + signature.length, expected, 0, expected.length);
    }
}
