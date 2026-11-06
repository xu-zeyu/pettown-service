package com.pettown.miniapp.file.controller;

import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.file.service.MiniappFileService;
import com.pettown.miniapp.file.vo.FileUploadResponse;
import com.pettown.miniapp.file.vo.ImageContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/miniapp/files")
@Tag(name = "小程序图片上传")
@SecurityRequirement(name = "bearerAuth")
public class MiniappFileController {
    private final MiniappFileService fileService;

    public MiniappFileController(MiniappFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传图片（头像/宠物照）")
    public Result<FileUploadResponse> upload(@RequestPart("file") MultipartFile file) throws IOException {
        String userId = PetTownToken.USER.getLoginIdAsString();
        return Result.success(fileService.upload(userId, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "读取已上传图片")
    public ResponseEntity<byte[]> read(@PathVariable String fileId) {
        PetTownToken.USER.checkLogin();
        ImageContent image = fileService.read(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
