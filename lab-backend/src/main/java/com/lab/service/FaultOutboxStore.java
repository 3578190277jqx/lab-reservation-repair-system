package com.lab.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 故障受理的事务性消息。必须与预约取消和维修记录使用同一事务。 */
@Repository
public class FaultOutboxStore {
    private final JdbcTemplate jdbc;
    public FaultOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int recordAffected(Long orderId, Long equipmentId, LocalDateTime now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("故障通知必须在工单受理事务内创建");
        }
        Timestamp time = Timestamp.valueOf(now);
        return jdbc.update("INSERT INTO fault_notification_outbox " +
                "(repair_order_id,reservation_id,student_id,equipment_id,lab_id,start_time,delivery_status,attempts,created_at,updated_at) " +
                // 调用方已锁定设备；在同一事务取得当时的实验室，不能用未来的设备归属授权历史事件。
                "SELECT ?,r.id,r.student_id,r.equipment_id,e.lab_id,r.start_time,0,0,?,? FROM reservation r " +
                "JOIN equipment e ON e.id=r.equipment_id " +
                "WHERE r.equipment_id=? AND r.deleted=0 AND r.status IN (0,1) AND r.end_time>?",
                orderId, time, time, equipmentId, time);
    }
}
