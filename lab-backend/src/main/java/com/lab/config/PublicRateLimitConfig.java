package com.lab.config;
import com.lab.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
@Configuration
@RequiredArgsConstructor
public class PublicRateLimitConfig implements WebMvcConfigurer {
 private final RateLimitService limiter;
 @Override public void addInterceptors(InterceptorRegistry registry){
  registry.addInterceptor(new HandlerInterceptor(){
   @Override public boolean preHandle(HttpServletRequest q,HttpServletResponse p,Object h){
    // Do not trust client supplied X-Forwarded-For. Nginx also limits true remote IP.
    String path=q.getRequestURI();
    if(path.equals("/user/login")) limiter.check("login-ip",q.getRemoteAddr(),60,60);
    // Behind an owned proxy this is an aggregate peer cap. True client IP is limited by Nginx;
    // per-account/per-recipient caps remain in the services, without trusting client headers.
    else if(path.equals("/user/register")) limiter.check("register-ip",q.getRemoteAddr(),100,3600);
    else if(path.startsWith("/user/recovery")) limiter.check("recovery-ip",q.getRemoteAddr(),300,3600);
    return true;
   }
  }).addPathPatterns("/user/login","/user/register","/user/recovery/**");
 }
}
