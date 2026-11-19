-- 独立审计不设业务外键和逻辑删除，不因用户/设备变化丢失历史。
CREATE TABLE operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作审计ID',
    operator_id BIGINT NULL COMMENT '操作用户ID，身份不可用时为空',
    operator_name VARCHAR(64) NULL COMMENT '操作人用户名快照',
    module VARCHAR(40) NOT NULL COMMENT '业务模块',
    action VARCHAR(100) NOT NULL COMMENT '操作标识',
    request_method VARCHAR(10) NULL COMMENT 'HTTP方法',
    request_path VARCHAR(255) NULL COMMENT '接口路径，不包含查询字符串',
    target_id VARCHAR(64) NULL COMMENT '白名单业务数值主键',
    ip VARCHAR(64) NULL COMMENT '直连客户端地址，不直接信任代理请求头',
    trace_id VARCHAR(64) NULL COMMENT '请求追踪标识',
    success TINYINT NOT NULL COMMENT '1成功，0失败',
    result_code VARCHAR(40) NULL COMMENT '结果码，不存异常消息或响应体',
    duration_ms BIGINT NOT NULL COMMENT '耗时，毫秒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operation_module_result (module, success, id),
    KEY idx_operation_actor_time (operator_id, create_time),
    KEY idx_operation_trace (trace_id)
);
