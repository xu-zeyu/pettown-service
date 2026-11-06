package com.pettown.miniapp.file.service;

public interface UploadQuota {
    /** 原子预留当日一次上传名额，返回用于失败回退的当日键。 */
    String reserve(String userId);

    void release(String reservationKey);
}
