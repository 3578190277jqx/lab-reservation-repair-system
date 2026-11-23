-- V5 历史事件没有记录发生时的实验室，不能用设备当前归属推测历史范围。
-- 旧事件保持 NULL，由超级管理员处理；新事件由受理事务写入实验室快照。
ALTER TABLE fault_notification_outbox
  ADD COLUMN lab_id BIGINT DEFAULT NULL COMMENT '故障事件发生时的实验室ID，NULL为历史归属未知';

CREATE INDEX idx_fault_lab_snapshot ON fault_notification_outbox (lab_id, confirmed_at, contacted_at, start_time);

ALTER TABLE fault_notification_outbox
  ADD CONSTRAINT fk_fault_lab_snapshot FOREIGN KEY (lab_id) REFERENCES lab(id);
