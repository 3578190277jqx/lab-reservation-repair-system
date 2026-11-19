package com.lab.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.entity.*;
import com.lab.mapper.*;
import com.lab.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** 仅处理业务层已授权的记录。每页最多两次关联查询，不逐条访问数据库。 */
@Service @RequiredArgsConstructor
public class ResourceViewService {
    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private Map<Long, Equipment> equipment(Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return unique.isEmpty() ? Collections.emptyMap() : equipmentMapper.selectBatchIds(unique).stream()
            .collect(Collectors.toMap(Equipment::getId, Function.identity()));
    }
    private Map<Long, Lab> labs(Collection<Long> ids) {
        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return unique.isEmpty() ? Collections.emptyMap() : labMapper.selectBatchIds(unique).stream()
            .collect(Collectors.toMap(Lab::getId, Function.identity()));
    }
    private <T,R> Page<R> page(Page<T> source, Function<T,R> convert) {
        Page<R> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(convert).collect(Collectors.toList()));
        return result;
    }
    public Page<EquipmentView> equipmentPage(Page<Equipment> source) {
        Map<Long,Lab> labs = labs(source.getRecords().stream().map(Equipment::getLabId).collect(Collectors.toList()));
        return page(source, row -> { EquipmentView view = new EquipmentView(); BeanUtils.copyProperties(row,view);
            Lab lab=labs.get(row.getLabId()); if(lab!=null){view.setLabName(lab.getName());view.setLabLocation(lab.getLocation());} return view; });
    }
    public EquipmentView equipmentDetail(Equipment row) { Page<Equipment> p=new Page<>();p.setRecords(Collections.singletonList(row));return equipmentPage(p).getRecords().get(0); }
    public Page<ReservationView> reservationPage(Page<Reservation> source) {
        Map<Long,Equipment> devices=equipment(source.getRecords().stream().map(Reservation::getEquipmentId).collect(Collectors.toList()));
        Map<Long,Lab> labs=labs(devices.values().stream().map(Equipment::getLabId).collect(Collectors.toList()));
        return page(source,row -> { ReservationView view=new ReservationView();BeanUtils.copyProperties(row,view);
            Equipment e=devices.get(row.getEquipmentId());if(e!=null){view.setEquipmentName(e.getName());view.setEquipmentCode(e.getEquipmentCode());
                Lab l=labs.get(e.getLabId());if(l!=null){view.setLabName(l.getName());view.setLabLocation(l.getLocation());}}return view; });
    }
    public ReservationView reservationDetail(Reservation row) { Page<Reservation> p=new Page<>();p.setRecords(Collections.singletonList(row));return reservationPage(p).getRecords().get(0); }
    public Page<RepairOrderView> repairPage(Page<RepairOrder> source) {
        Map<Long,Equipment> devices=equipment(source.getRecords().stream().map(RepairOrder::getEquipmentId).collect(Collectors.toList()));
        Map<Long,Lab> labs=labs(devices.values().stream().map(Equipment::getLabId).collect(Collectors.toList()));
        return page(source,row -> { RepairOrderView view=new RepairOrderView();BeanUtils.copyProperties(row,view);
            Equipment e=devices.get(row.getEquipmentId());if(e!=null){view.setEquipmentName(e.getName());view.setEquipmentCode(e.getEquipmentCode());
                Lab l=labs.get(e.getLabId());if(l!=null){view.setLabName(l.getName());view.setLabLocation(l.getLocation());}}return view; });
    }
    public RepairOrderView repairDetail(RepairOrder row) { Page<RepairOrder> p=new Page<>();p.setRecords(Collections.singletonList(row));return repairPage(p).getRecords().get(0); }
}
