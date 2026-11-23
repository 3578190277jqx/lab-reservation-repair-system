package com.lab.observability;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpring;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.lab.common.BusinessException;
import com.lab.common.GlobalExceptionHandler;
import com.lab.common.ResultCode;
import com.lab.config.SaTokenConfig;
import com.lab.controller.FaultNotificationController;
import com.lab.entity.User;
import com.lab.service.FaultNotificationService;
import com.lab.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import java.util.List;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FaultNotificationMvcAccessTest {
    private MockMvc mvc;
    private UserService users;
    private FaultNotificationService notifications;
    private SaTokenContext previousContext;
    private SaTokenDao previousDao;
    private StpInterface previousRoles;
    private cn.dev33.satoken.config.SaTokenConfig previousConfig;

    @BeforeEach void init() {
        previousContext = SaManager.getSaTokenContext();
        previousDao = SaManager.getSaTokenDao();
        previousRoles = SaManager.getStpInterface();
        previousConfig = SaManager.getConfig();
        SaManager.setSaTokenContext(new SaTokenContextForSpring());
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.setConfig(new cn.dev33.satoken.config.SaTokenConfig()
                .setTokenName("Authorization").setTokenPrefix("Bearer"));
        users = mock(UserService.class);
        notifications = mock(FaultNotificationService.class);
        when(users.requireActiveUser(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            if (id == 3L) throw new BusinessException(ResultCode.FORBIDDEN);
            User user = new User();
            user.setId(id); user.setAuthVersion(0); user.setRole(id == 1L ? "admin" : "student");
            return user;
        });
        SaTokenConfig configuration = new SaTokenConfig(users);
        SaManager.setStpInterface(configuration.stpInterface());
        InterceptorRegistry registry = new InterceptorRegistry();
        configuration.addInterceptors(registry);
        List<?> registered = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        HandlerInterceptor[] interceptors = registered.stream().map(item -> {
            if (!(item instanceof MappedInterceptor)) return (HandlerInterceptor) item;
            MappedInterceptor mapped = (MappedInterceptor) item;
            return new HandlerInterceptor() {
                @Override public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                        javax.servlet.http.HttpServletResponse response, Object handler) throws Exception {
                    return !mapped.matches(request) || mapped.getInterceptor().preHandle(request, response, handler);
                }
            };
        }).toArray(HandlerInterceptor[]::new);
        mvc = MockMvcBuilders.standaloneSetup(new FaultNotificationController(notifications))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(interceptors).build();
    }

    @AfterEach void reset() {
        RequestContextHolder.resetRequestAttributes();
        SaManager.setSaTokenContext(previousContext); SaManager.setSaTokenDao(previousDao);
        SaManager.setStpInterface(previousRoles); SaManager.setConfig(previousConfig);
    }

    @Test void anonymousCannotReadOrChangeNotifications() throws Exception {
        mvc.perform(get("/notification/my")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notification/1/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notification/contact-todos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notification/1/contacted")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test void studentCannotAccessContactTodoAndAdminCannotConfirmForStudent() throws Exception {
        mvc.perform(get("/notification/contact-todos").header("Authorization", "Bearer " + tokenFor(2, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/notification/1/confirm").header("Authorization", "Bearer " + tokenFor(1, 0)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test void disabledAccountCannotReadNotifications() throws Exception {
        mvc.perform(get("/notification/my").header("Authorization", "Bearer " + tokenFor(3, 0)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test void sessionWithOldAuthVersionCannotReadNotifications() throws Exception {
        mvc.perform(get("/notification/my").header("Authorization", "Bearer " + tokenFor(2, 1)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test void correctRoleCanUseItsEndpoint() throws Exception {
        mvc.perform(get("/notification/my").header("Authorization", "Bearer " + tokenFor(2, 0)))
                .andExpect(status().isOk());
        mvc.perform(get("/notification/contact-todos").header("Authorization", "Bearer " + tokenFor(1, 0)))
                .andExpect(status().isOk());
        verify(notifications).my(2L, 1L, 20L);
        verify(notifications).contactTodos(1L, true, 1L, 20L);
    }

    private String tokenFor(long id, int version) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        StpUtil.login(id); StpUtil.getTokenSession().set("authVersion", version);
        String token = StpUtil.getTokenValue(); RequestContextHolder.resetRequestAttributes(); return token;
    }
}
