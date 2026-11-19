package com.lab.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.lab.entity.User;
import com.lab.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 操作人来自服务端登录态，忽略请求体传入的 operatorId。 */
@Component
@RequiredArgsConstructor
public class AuditActorResolver {
    private final UserService users;

    public AuditActor resolve() {
        if (!StpUtil.isLogin()) return new AuditActor(null, "anonymous");
        Long id = StpUtil.getLoginIdAsLong();
        User user = users.requireActiveUser(id);
        return new AuditActor(id, user.getUsername());
    }
}
