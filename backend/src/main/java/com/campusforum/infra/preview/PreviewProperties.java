package com.campusforum.infra.preview;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "preview")
public class PreviewProperties {

    /** Office 文档预览服务 URL（如 KKFileView） */
    private String officeServiceUrl = "http://localhost:8012/onlinePreview";

    /** 最大可预览文件大小（字节），默认 50MB */
    private long maxPreviewSize = 50 * 1024 * 1024L;
}
