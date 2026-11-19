package com.lab.observability;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpring;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.common.GlobalExceptionHandler;
import com.lab.config.SaTokenConfig;
import com.lab.controller.OperationLogController;
import com.lab.entity.User;
import com.lab.mapper.OperationLogMapper;
import com.lab.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagementAccessTest {
    private MockMvc mvc;
    private UserService users;
    private SaTokenContext previousContext;
    private SaTokenDao previousDao;
    private StpInterface previousRoles;
    private cn.dev33.satoken.config.SaTokenConfig previousConfig;

    @BeforeEach void init() {
        previousContext = SaManager.getSaTokenContext(); previousDao = SaManager.getSaTokenDao();
        previousRoles = SaManager.getStpInterface(); previousConfig = SaManager.getConfig();
        SaManager.setSaTokenContext(new SaTokenContextForSpring());
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.setConfig(new cn.dev33.satoken.config.SaTokenConfig().setTokenName("Authorization").setTokenPrefix("Bearer"));
        users = mock(UserService.class);
        when(users.requireActiveUser(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); User user = new User(); user.setId(id);
            user.setUsername("user" + id); user.setAuthVersion(0); user.setRole(id == 1L ? "admin" : "student");
            return user;
        });
        SaTokenConfig configuration = new SaTokenConfig(users);
        SaManager.setStpInterface(configuration.stpInterface());
        InterceptorRegistry registry = new InterceptorRegistry(); configuration.addInterceptors(registry);
        List<?> registered = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        // MVC 的 HandlerMapping 会先执行 matches；直接嵌套 MappedInterceptor 会绕过路径匹配。
        HandlerInterceptor[] interceptors = registered.stream().map(item -> {
            if (!(item instanceof MappedInterceptor)) return (HandlerInterceptor) item;
            MappedInterceptor mapped = (MappedInterceptor) item;
            return new HandlerInterceptor() {
                @Override
                public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                                         javax.servlet.http.HttpServletResponse response,
                                         Object handler) throws Exception {
                    return !mapped.matches(request) || mapped.getInterceptor().preHandle(request, response, handler);
                }
            };
        }).toArray(HandlerInterceptor[]::new);
        mvc = MockMvcBuilders.standaloneSetup(new Endpoints(), new OperationLogController(mock(OperationLogMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptors).build();
    }

    @AfterEach void reset() {
        RequestContextHolder.resetRequestAttributes();
        SaManager.setSaTokenContext(previousContext); SaManager.setSaTokenDao(previousDao);
        SaManager.setStpInterface(previousRoles); SaManager.setConfig(previousConfig);
    }

    @Test void anonymousCanOnlyReadExactHealthEndpoint() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/operation-log/page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health/db")).andExpect(status().is4xxClientError());
    }

    @Test void studentCannotReadMetricsOrAuditLog() throws Exception {
        String token = tokenFor(2L, 0);
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/operation-log/page").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test void administratorCanReadInfoAndMetricsButNeverSecrets() throws Exception {
        String token = tokenFor(1L, 0);
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/heapdump").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test void invalidatedAdminSessionCannotReadMetrics() throws Exception {
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + tokenFor(1L, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test void administratorAuditPaginationRejectsOversizedQueries() throws Exception {
        mvc.perform(get("/operation-log/page").param("size", "101").header("Authorization", "Bearer " + tokenFor(1L, 0)))
                .andExpect(status().isBadRequest());
    }

    private String tokenFor(Long id, int version) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        StpUtil.login(id); StpUtil.getTokenSession().set("authVersion", version);
        String token = StpUtil.getTokenValue(); RequestContextHolder.resetRequestAttributes(); return token;
    }

    @RestController
    public static class Endpoints {
        @GetMapping("/actuator/{endpoint}")
        public String endpoint(@PathVariable String endpoint) { return "ok"; }
        @GetMapping("/actuator/health/db")
        public String details() { return "secret"; }
    }
}
