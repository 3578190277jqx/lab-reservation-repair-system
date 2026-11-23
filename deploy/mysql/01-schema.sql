-- MySQL 8.0 schema for a NEW Docker data volume.
-- Schema only: no existing user passwords or business records are exported.
-- MYSQL_DATABASE is created and selected by the official MySQL entrypoint.
-- This script deliberately has no DROP TABLE / DROP DATABASE statements.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '登录用户名，全局唯一',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt密码哈希，禁止存储明文密码',
  `real_name` varchar(64) NOT NULL COMMENT '真实姓名',
  `student_no` varchar(32) DEFAULT NULL COMMENT '学号，非学生用户可为空',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(128) DEFAULT NULL COMMENT '电子邮箱',
  `email_verified` tinyint NOT NULL DEFAULT 0 COMMENT '邮箱是否验证：0否，1是',
  `phone_verified` tinyint NOT NULL DEFAULT 0 COMMENT '手机号是否验证：0否，1是',
  `auth_version` int NOT NULL DEFAULT 0 COMMENT '会话版本，重置密码或角色变更后递增',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '头像访问地址',
  `role` varchar(20) NOT NULL DEFAULT 'student' COMMENT '角色：admin超级管理员、lab_admin实验室管理员、student学生',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：0禁用，1正常',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_student_no` (`student_no`),
  KEY `idx_user_role_status` (`role`,`status`,`deleted`),
  CONSTRAINT `chk_user_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_user_role` CHECK ((`role` in (_utf8mb4'admin',_utf8mb4'lab_admin',_utf8mb4'student'))),
  CONSTRAINT `chk_user_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `lab` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '实验室ID',
  `lab_code` varchar(32) NOT NULL COMMENT '实验室编码，全局唯一',
  `name` varchar(100) NOT NULL COMMENT '实验室名称',
  `location` varchar(255) NOT NULL COMMENT '实验室位置',
  `admin_id` bigint DEFAULT NULL COMMENT '负责管理该实验室的用户ID',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `description` varchar(1000) DEFAULT NULL COMMENT '实验室介绍',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '实验室状态：0停用，1启用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lab_code` (`lab_code`),
  KEY `idx_lab_admin_id` (`admin_id`),
  KEY `idx_lab_status_deleted` (`status`,`deleted`),
  CONSTRAINT `fk_lab_admin` FOREIGN KEY (`admin_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_lab_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_lab_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室表';

CREATE TABLE `equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `lab_id` bigint NOT NULL COMMENT '所属实验室ID',
  `equipment_code` varchar(64) NOT NULL COMMENT '设备资产编号，全局唯一',
  `name` varchar(100) NOT NULL COMMENT '设备名称',
  `model` varchar(100) DEFAULT NULL COMMENT '设备型号',
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '设备制造商',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '设备状态：0可用，1已预约，2维修中，3已报废',
  `purchase_date` date DEFAULT NULL COMMENT '采购日期',
  `image_url` varchar(1024) DEFAULT NULL COMMENT '设备图片访问地址',
  `description` varchar(1000) DEFAULT NULL COMMENT '设备说明及使用注意事项',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_code` (`equipment_code`),
  KEY `idx_equipment_lab_status` (`lab_id`,`status`,`deleted`),
  CONSTRAINT `fk_equipment_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_equipment_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_equipment_status` CHECK ((`status` in (0,1,2,3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备表';

CREATE TABLE `reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预约ID',
  `student_id` bigint NOT NULL COMMENT '申请学生ID',
  `equipment_id` bigint NOT NULL COMMENT '预约设备ID',
  `start_time` datetime NOT NULL COMMENT '预约开始时间',
  `end_time` datetime NOT NULL COMMENT '预约结束时间',
  `purpose` varchar(500) DEFAULT NULL COMMENT '预约用途',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '预约状态：0待审核，1已通过，2已拒绝，3已取消，4爽约',
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_by` bigint DEFAULT NULL COMMENT '审核人用户ID',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核意见或拒绝原因',
  `sign_in_time` datetime DEFAULT NULL COMMENT '实际签到时间，为空表示未签到',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因',
  `no_show_time` datetime DEFAULT NULL COMMENT '标记爽约的时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_reservation_equipment_time` (`equipment_id`,`start_time`,`end_time`),
  KEY `idx_reservation_student_status` (`student_id`,`status`,`deleted`,`start_time`),
  KEY `idx_reservation_no_show` (`status`,`deleted`,`sign_in_time`,`start_time`),
  KEY `idx_reservation_audit_by` (`audit_by`),
  KEY `idx_reservation_apply_time` (`apply_time`),
  CONSTRAINT `fk_reservation_auditor` FOREIGN KEY (`audit_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reservation_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reservation_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_reservation_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_reservation_status` CHECK ((`status` in (0,1,2,3,4))),
  CONSTRAINT `chk_reservation_time` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备预约申请与审核表';

CREATE TABLE `blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '黑名单ID',
  `student_id` bigint NOT NULL COMMENT '被限制预约的学生ID',
  `reason` varchar(500) NOT NULL DEFAULT '累计爽约达到2次' COMMENT '加入黑名单的原因',
  `no_show_count` int NOT NULL DEFAULT '2' COMMENT '加入黑名单时的累计爽约次数快照',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封禁开始时间',
  `expire_time` datetime NOT NULL COMMENT '封禁到期时间，由业务层设置为开始时间加30天',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0已解除，1启用；启用时仍须校验封禁时间范围',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blacklist_student_id` (`student_id`),
  KEY `idx_blacklist_status_expire` (`status`,`deleted`,`expire_time`),
  CONSTRAINT `fk_blacklist_student` FOREIGN KEY (`student_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_blacklist_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_blacklist_no_show_count` CHECK ((`no_show_count` >= 0)),
  CONSTRAINT `chk_blacklist_status` CHECK ((`status` in (0,1))),
  CONSTRAINT `chk_blacklist_time` CHECK ((`expire_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生爽约黑名单表';

CREATE TABLE `repair_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '报修工单ID',
  `order_no` varchar(40) NOT NULL COMMENT '工单编号，由业务层生成，全局唯一',
  `equipment_id` bigint NOT NULL COMMENT '故障设备ID',
  `reporter_id` bigint NOT NULL COMMENT '报修人用户ID',
  `description` text NOT NULL COMMENT '故障现象描述',
  `image_url` varchar(1024) DEFAULT NULL COMMENT '故障图片访问地址',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '工单状态：0待报修（已提交待受理），1已受理，2维修中，3已完成',
  `handler_id` bigint DEFAULT NULL COMMENT '工单处理人用户ID',
  `ai_suggestion` text COMMENT '百度AI识别返回的故障大类提示，仅供参考',
  `accepted_time` datetime DEFAULT NULL COMMENT '工单受理时间',
  `repair_start_time` datetime DEFAULT NULL COMMENT '开始维修时间',
  `completed_time` datetime DEFAULT NULL COMMENT '维修完成时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_repair_order_no` (`order_no`),
  KEY `idx_repair_order_equipment_status` (`equipment_id`,`status`,`deleted`),
  KEY `idx_repair_order_reporter_time` (`reporter_id`,`create_time`),
  KEY `idx_repair_order_handler_status` (`handler_id`,`status`),
  KEY `idx_repair_order_status_time` (`status`,`deleted`,`create_time`),
  CONSTRAINT `fk_repair_order_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_repair_order_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_repair_order_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_repair_order_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_repair_order_status` CHECK ((`status` in (0,1,2,3)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='故障报修工单表';

CREATE TABLE `repair_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '维修记录ID',
  `repair_order_id` bigint NOT NULL COMMENT '所属报修工单ID',
  `operator_id` bigint NOT NULL COMMENT '本次操作人用户ID',
  `from_status` tinyint DEFAULT NULL COMMENT '流转前状态：NULL首次提交，0待报修，1已受理，2维修中',
  `to_status` tinyint NOT NULL COMMENT '流转后状态：0待报修，1已受理，2维修中，3已完成',
  `content` text NOT NULL COMMENT '操作说明、维修过程或维修结果',
  `repair_cost` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本次记录新增维修费用，单位元',
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '实际操作时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_repair_record_order_time` (`repair_order_id`,`operation_time`),
  KEY `idx_repair_record_operator_id` (`operator_id`),
  CONSTRAINT `fk_repair_record_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_repair_record_order` FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_repair_record_cost` CHECK ((`repair_cost` >= 0)),
  CONSTRAINT `chk_repair_record_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_repair_record_transition` CHECK ((((`from_status` is null) and (`to_status` = 0)) or ((`from_status` is not null) and (((`from_status` = 0) and (`to_status` = 1)) or ((`from_status` = 1) and (`to_status` = 2)) or ((`from_status` = 2) and (`to_status` = 3))))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单状态流转与维修记录表';

CREATE TABLE `usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '使用日志ID',
  `reservation_id` bigint NOT NULL COMMENT '关联预约ID，一次预约对应一条使用日志',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '实际开始使用时间',
  `end_time` datetime DEFAULT NULL COMMENT '实际结束使用时间，未结束时为空',
  `remark` varchar(500) DEFAULT NULL COMMENT '使用备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_usage_log_reservation_id` (`reservation_id`),
  KEY `idx_usage_log_time` (`start_time`,`end_time`),
  CONSTRAINT `fk_usage_log_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_usage_log_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_usage_log_time` CHECK (((`end_time` is null) or (`end_time` >= `start_time`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备实际使用日志表';
