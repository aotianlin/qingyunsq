package com.campusforum.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 1xxxx 业务错误
    USER_NOT_FOUND(10001, "用户不存在"),       // 仅保留给已认证管理员操作使用，登录路径请使用 INVALID_CREDENTIALS
    WRONG_PASSWORD(10002, "密码错误"),         // 仅保留给已认证管理员操作使用，登录路径请使用 INVALID_CREDENTIALS
    USER_BANNED(10003, "账号已被封禁"),        // 仅保留给已认证管理员操作使用，登录路径请使用 INVALID_CREDENTIALS
    SPACE_NOT_FOUND(10101, "空间不存在"),
    SPACE_FULL(10102, "空间成员已满"),
    POST_NOT_FOUND(10201, "帖子不存在"),
    COMMENT_NOT_FOUND(10301, "评论不存在"),
    BOUNTY_ALREADY_SETTLED(10401, "悬赏积分已结算"),
    CHALLENGE_NOT_FOUND(10501, "打卡挑战不存在"),
    ALREADY_CHECKED_IN(10502, "今日已打卡"),
    RESOURCE_NOT_FOUND(10601, "资源不存在"),
    QUESTION_ALREADY_SOLVED(10701, "问题已有采纳答案"),

    // 4xxxx 客户端错误
    BAD_REQUEST(40000, "请求参数错误"),
    TENANT_NOT_RESOLVED(40010, "无法识别租户"),
    TENANT_NOT_FOUND(40011, "租户不存在或已停用"),
    UNAUTHORIZED(40100, "未登录"),
    INVALID_CREDENTIALS(40101, "邮箱或密码错误"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    RATE_LIMITED(42900, "请求过于频繁"),

    // 5xxxx 服务端错误
    INTERNAL_ERROR(50000, "服务器内部错误"),
    AI_SERVICE_UNAVAILABLE(50001, "AI 服务暂不可用"),
    SEARCH_UNAVAILABLE(50002, "搜索服务暂不可用"),
    STORAGE_ERROR(50003, "文件存储异常"),
    TENANT_VIOLATION(51001, "租户数据隔离异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
