package com.lab.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.context.SaHolder;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.entity.User;
import com.lab.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final UserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注解鉴权由SaInterceptor执行；回调同时检查账号是否仍然有效。
        registry.addInterceptor(new SaInterceptor(handler -> {
                    String path = SaHolder.getRequest().getRequestPath();
                    // 管理入口只允许明确的端点，禁止未来误开放env、heapdump等敏感端点。
                    if ("/actuator".equals(path) || path.startsWith("/actuator/")) {
                        if (!("/actuator/info".equals(path) || "/actuator/metrics".equals(path)
                                || path.startsWith("/actuator/metrics/"))) {
                            throw new BusinessException(ResultCode.NOT_FOUND);
                        }
                    }
                    StpUtil.checkLogin();
                    User current = userService.requireActiveUser(StpUtil.getLoginIdAsLong());
                    Object stored = StpUtil.getTokenSession().get("authVersion");
                    int tokenVersion = stored instanceof Number ? ((Number)stored).intValue() : 0;
                    int accountVersion = current.getAuthVersion()==null ? 0 : current.getAuthVersion();
                    if(tokenVersion != accountVersion) throw new com.lab.common.BusinessException(com.lab.common.ResultCode.UNAUTHORIZED);
                    if (path.startsWith("/actuator/") || path.startsWith("/operation-log/")) {
                        StpUtil.checkRole("admin");
                    }
                }))
                .addPathPatterns("/user/**", "/api/**", "/lab/**", "/equipment/**", "/reservation/**", "/repair/**", "/notification/**", "/file/**", "/stats/**", "/assistant/**", "/operation-log/**", "/actuator/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/recovery/options",
                        "/user/recovery/code",
                        "/user/recovery/reset",
                        "/api/health",
                        "/actuator/health"
                );
    }

    @Bean
    public StpInterface stpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                // 当前按角色授权，使用 @SaCheckRole；权限码尚未定义时不授予权限。
                return Collections.emptyList();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                if (!StpUtil.TYPE.equals(loginType)) {
                    return Collections.emptyList();
                }
                Long userId;
                try {
                    userId = Long.valueOf(String.valueOf(loginId));
                } catch (NumberFormatException ex) {
                    return Collections.emptyList();
                }
                // 每次读取数据库，立即反映角色变化、禁用和逻辑删除。
                User user = userService.requireActiveUser(userId);
                return Collections.singletonList(user.getRole());
            }
        };
    }
}
