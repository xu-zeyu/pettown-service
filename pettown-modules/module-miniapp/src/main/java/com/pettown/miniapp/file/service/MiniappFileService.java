package com.pettown.miniapp.file.service;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.file.service.FileService;
import com.pettown.miniapp.file.config.MiniappFileProperties;
import com.pettown.miniapp.file.vo.FileUploadResponse;
import com.pettown.miniapp.file.vo.ImageContent;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 小程序图片上传与鉴权代理读取；对象键随机生成，不信任原始文件名。 */
@Service
public class MiniappFileService {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String OBJECT_PREFIX = "miniapp/images/";
    private static final Pattern FILE_ID = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final byte[] PNG_MAGIC = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final ObjectProvider<FileService> fileService;
    private final UploadQuota uploadQuota;
    private final MiniappFileProperties properties;

    @Autowired
    public MiniappFileService(ObjectProvider<FileService> fileService, UploadQuota uploadQuota,
            MiniappFileProperties properties) {
        this.fileService = fileService;
        this.uploadQuota = uploadQuota;
        this.properties = properties;
    }

    public FileUploadResponse upload(String userId, String originalFilename, byte[] content) {
        String extension = validateImage(originalFilename, content);
        FileService storage = storage();
        String baseUrl = validatedBaseUrl();
        String fileId = UUID.randomUUID() + "." + extension;
        String reservation = uploadQuota.reserve(userId);
        try {
            storage.uploadObject(OBJECT_PREFIX + fileId, content);
        } catch (RuntimeException exception) {
            uploadQuota.release(reservation);
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "文件存储失败，今日额度已释放");
        }
        return new FileUploadResponse(fileId, baseUrl + "/api/miniapp/files/" + fileId);
    }

    public ImageContent read(String fileId) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "文件不存在");
        }
        byte[] content = storage().downloadObject(OBJECT_PREFIX + fileId);
        if (content == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "文件不存在");
        }
        String extension = fileId.substring(fileId.lastIndexOf('.') + 1);
        return new ImageContent(content, contentTypeOf(extension));
    }

    /** 校验地址必须为本系统上传接口返回的图片 URL；不回查存储存在性。 */
    public void requireOwnImageUrl(String url) {
        String prefix = validatedBaseUrl() + "/api/miniapp/files/";
        if (url == null || !url.startsWith(prefix) || !FILE_ID.matcher(url.substring(prefix.length())).matches()) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "头像必须为本系统上传接口返回的地址");
        }
    }

    private String validateImage(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "文件内容不能为空");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "图片大小不能超过 5MB");
        }
        String extension = extensionOf(originalFilename);
        if (extension == null || !EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "仅支持 jpg/png/webp 格式图片");
        }
        boolean contentMatches = switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
            case "png" -> startsWith(content, PNG_MAGIC);
            case "webp" -> isWebp(content);
            default -> false;
        };
        if (!contentMatches) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "图片内容与文件格式不一致");
        }
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private FileService storage() {
        FileService storage = fileService.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "文件服务未配置，暂不能提供图片能力");
        }
        return storage;
    }

    private String validatedBaseUrl() {
        String raw = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl().trim();
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            URI uri = URI.create(raw);
            boolean https = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
            boolean localhost = "http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (raw.isEmpty() || (!https && !localhost)) {
                throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "文件服务未正确配置访问基址");
            }
            return raw;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.SERVICE_UNAVAILABLE, "文件服务未正确配置访问基址");
        }
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return null;
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte... magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "文件不存在");
        };
    }
}
