package com.lab.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 服务端生成的统计范围；不能通过接口请求绑定 managerId 或时间戳。 */
@Data
public class StatsScope {
    private Long managerId;
    private Long labId;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private LocalDateTime effectiveEndTime;
}
