package com.lab.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 仅声明操作元数据和数值主键的来源，禁止录入请求体/响应体/异常消息。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAudit {
    String module();
    String action();
    int targetArgument() default -1;
    String targetField() default "";
}
