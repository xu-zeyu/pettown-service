package com.pettown.common.file.service;

public interface FileService {
    String upload(byte[] content, String originalFilename);
    byte[] download(String fileUrl);
    void uploadObject(String objectKey, byte[] content);
    byte[] downloadObject(String objectKey);
}
