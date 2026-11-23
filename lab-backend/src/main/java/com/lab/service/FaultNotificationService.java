package com.lab.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站内通知访问；角色由入口校验，所有者和实验室范围在每条 SQL 中复核。 */
@Service
public class FaultNotificationService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public FaultNotificationService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    private static final String FROM=" FROM fault_notification_outbox n JOIN equipment e ON e.id=n.equipment_id " +
            "LEFT JOIN lab l ON l.id=n.lab_id JOIN reservation r ON r.id=n.reservation_id JOIN `user` u ON u.id=n.student_id ";
    private static final String SELECT="SELECT n.id,n.repair_order_id,n.reservation_id,n.equipment_id,e.name AS equipment_name,"+
            "n.lab_id AS lab_id,n.start_time,r.end_time,n.delivery_status,n.confirmed_at,n.contacted_at,n.created_at,"+
            "u.id AS student_id,u.real_name AS student_name,u.phone AS student_phone ";

    public Page<Map<String,Object>> my(long actor,long current,long size) {
        return query(" WHERE n.student_id=? AND n.delivery_status=1",new Object[]{actor},false,current,size);
    }
    public Page<Map<String,Object>> contactTodos(long actor,boolean admin,long current,long size) {
        LocalDateTime now=LocalDateTime.now(clock);
        String where=" WHERE n.confirmed_at IS NULL AND n.contacted_at IS NULL AND r.end_time>? AND n.start_time<=?";
        List<Object> params=new ArrayList<>();params.add(Timestamp.valueOf(now));params.add(Timestamp.valueOf(now.plusHours(24)));
        if (!admin) { where+=" AND l.admin_id=? AND l.deleted=0";params.add(actor); }
        return query(where,params.toArray(),true,current,size);
    }
    public void confirm(long actor,long id) {
        int changed=jdbc.update("UPDATE fault_notification_outbox SET confirmed_at=?,updated_at=? " +
                "WHERE id=? AND student_id=? AND delivery_status=1 AND confirmed_at IS NULL",time(),time(),id,actor);
        if (changed==0 && count("SELECT COUNT(*) FROM fault_notification_outbox WHERE id=? AND student_id=? AND delivery_status=1",id,actor)==0)
            throw new BusinessException(ResultCode.NOT_FOUND);
    }
    public void contacted(long actor,boolean admin,long id) {
        String allowed=admin?"":" AND EXISTS (SELECT 1 FROM lab l WHERE l.id=fault_notification_outbox.lab_id AND l.admin_id=? AND l.deleted=0)";
        List<Object> params=new ArrayList<>();params.add(time());params.add(actor);params.add(time());params.add(id);if(!admin)params.add(actor);
        int changed=jdbc.update("UPDATE fault_notification_outbox SET contacted_at=?,contacted_by=?,updated_at=? WHERE id=? AND contacted_at IS NULL"+allowed,params.toArray());
        List<Object> check=new ArrayList<>();check.add(id);if(!admin)check.add(actor);
        if(changed==0 && count("SELECT COUNT(*) FROM fault_notification_outbox WHERE id=?"+allowed,check.toArray())==0)
            throw new BusinessException(ResultCode.NOT_FOUND);
    }
    private int count(String sql,Object... args) { Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n; }
    private Timestamp time(){return Timestamp.valueOf(LocalDateTime.now(clock));}
    private Page<Map<String,Object>> query(String where,Object[] params,boolean manager,long current,long size) {
        long page=Math.max(1,current), limit=Math.max(1,Math.min(100,size));
        long total=count("SELECT COUNT(*)"+FROM+where,params);
        List<Object> args=new ArrayList<>();java.util.Collections.addAll(args,params);args.add(limit);args.add((page-1)*limit);
        List<Map<String,Object>> rows=jdbc.query(SELECT+FROM+where+" ORDER BY n.id DESC LIMIT ? OFFSET ?",(rs,i)->row(rs,manager),args.toArray());
        Page<Map<String,Object>> result=new Page<>(page,limit,total);result.setRecords(rows);return result;
    }
    private static Map<String,Object> row(ResultSet r,boolean manager)throws SQLException {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",r.getLong("id"));out.put("repairOrderId",r.getLong("repair_order_id"));out.put("reservationId",r.getLong("reservation_id"));
        out.put("equipmentId",r.getLong("equipment_id"));out.put("equipmentName",r.getString("equipment_name"));
        long labId=r.getLong("lab_id");out.put("labId",r.wasNull()?null:labId);
        out.put("startTime",r.getTimestamp("start_time"));out.put("endTime",r.getTimestamp("end_time"));out.put("deliveryStatus",r.getInt("delivery_status"));
        out.put("confirmedAt",r.getTimestamp("confirmed_at"));out.put("contactedAt",r.getTimestamp("contacted_at"));out.put("createdAt",r.getTimestamp("created_at"));
        out.put("content","因设备故障取消预约，请查看并确认。");
        if(manager){out.put("studentId",r.getLong("student_id"));out.put("studentName",r.getString("student_name"));out.put("studentPhone",r.getString("student_phone"));}
        return out;
    }
}
