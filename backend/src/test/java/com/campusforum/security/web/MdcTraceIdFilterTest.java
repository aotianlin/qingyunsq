package com.campusforum.security.web;

import com.campusforum.common.R;
import com.campusforum.infra.web.MdcTraceIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link MdcTraceIdFilter} 单元测试（对应 bugfix.md 漏洞 31 / tasks.md T9.3）。
 *
 * <p>用 standalone MockMvc 把 MdcTraceIdFilter 接到一个最简 controller 上，
 * 在不启动完整 Spring 上下文的情况下验证 4 个核心行为：</p>
 * <ol>
 *   <li>response 的 {@code X-Trace-Id} header 与响应体 {@code R.traceId} 相同；</li>
 *   <li>合法入站头被透传（{@code abc12345-def}）；</li>
 *   <li>非法入站头被忽略并重新生成（{@code short}）；</li>
 *   <li>请求结束后 MDC 已清理。</li>
 * </ol>
 */
class MdcTraceIdFilterTest {

    /** 用于直接复用 MdcTraceIdFilter 中的常量，避免拼写漂移。 */
    private static final String HEADER = MdcTraceIdFilter.HEADER_TRACE_ID;

    /** Jackson ObjectMapper：解析 JSON 响应体，断言其中的 traceId 字段。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // 测试前清空 MDC，避免上一条测试残留影响断言
        MDC.clear();

        MdcTraceIdFilter filter = new MdcTraceIdFilter();
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(filter)
                .build();
    }

    @AfterEach
    void tearDown() {
        // 兜底再清一次，避免本测试 finally 异常导致后续测试串味
        MDC.clear();
    }

    /**
     * 测试 1：response 头部的 X-Trace-Id 与 R.traceId 字段一致。
     */
    @Test
    @DisplayName("response_header_matches_R_traceId：响应头 X-Trace-Id 与响应体 R.traceId 字段相同")
    void response_header_matches_R_traceId() throws Exception {
        MvcResult result = mvc.perform(get("/test/echo"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER))
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(HEADER);
        String body = result.getResponse().getContentAsString();
        JsonNode node = OBJECT_MAPPER.readTree(body);
        String bodyTraceId = node.get("traceId").asText();

        assertThat(headerTraceId).isNotBlank();
        assertThat(bodyTraceId).isEqualTo(headerTraceId);
    }

    /**
     * 测试 2：客户端传入合法 X-Trace-Id 时原样透传。
     */
    @Test
    @DisplayName("inbound_header_isReused_whenValid：合法入站 traceId 被透传")
    void inbound_header_isReused_whenValid() throws Exception {
        // abc12345-def 共 12 位，命中 ^[a-zA-Z0-9-]{8,64}$
        String inbound = "abc12345-def";

        MvcResult result = mvc.perform(get("/test/echo")
                        .header(HEADER, inbound))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, inbound))
                .andReturn();

        JsonNode node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("traceId").asText()).isEqualTo(inbound);
    }

    /**
     * 测试 3：客户端传入非法 X-Trace-Id（长度 < 8）时被忽略并重新生成 16 字符 UUID。
     */
    @Test
    @DisplayName("inbound_header_isRegenerated_whenInvalid：非法入站 traceId 被重新生成")
    void inbound_header_isRegenerated_whenInvalid() throws Exception {
        String invalid = "short"; // 仅 5 个字符，低于正则下限 8

        MvcResult result = mvc.perform(get("/test/echo")
                        .header(HEADER, invalid))
                .andExpect(status().isOk())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(HEADER);
        assertThat(headerTraceId).isNotEqualTo(invalid);
        // 重新生成的 traceId 由 16 字符 UUID 片段构成，仅含字母数字
        assertThat(headerTraceId).hasSize(16).matches("[a-zA-Z0-9]{16}");
    }

    /**
     * 测试 4：请求结束后 MDC 必须清理干净，避免线程池复用导致跨请求串味。
     */
    @Test
    @DisplayName("mdc_isCleaned_afterRequest：请求结束后 MDC 中的 traceId/tenantId/userId 全部为 null")
    void mdc_isCleaned_afterRequest() throws Exception {
        mvc.perform(get("/test/echo"))
                .andExpect(status().isOk());

        assertThat(MDC.get(MdcTraceIdFilter.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(MdcTraceIdFilter.MDC_TENANT_ID)).isNull();
        assertThat(MDC.get(MdcTraceIdFilter.MDC_USER_ID)).isNull();
    }

    /**
     * 测试用最简 controller：
     * 直接返回 R.ok("payload")，由 R 构造函数完成 traceId 提取，
     * 让响应体 traceId 与 Filter 写入响应头的 traceId 形成对比。
     */
    @RestController
    static class TestController {

        @GetMapping("/test/echo")
        public R<String> echo() {
            return R.ok("payload");
        }
    }
}
