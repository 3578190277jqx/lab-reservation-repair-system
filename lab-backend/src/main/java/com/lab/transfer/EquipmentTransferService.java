package com.lab.transfer;

import cn.dev33.satoken.stp.StpUtil;
import com.lab.cache.EquipmentPageCache;
import com.lab.common.BusinessException;
import com.lab.common.ResultCode;
import com.lab.dto.EquipmentPageQuery;
import com.lab.entity.Equipment;
import com.lab.entity.Lab;
import com.lab.mapper.EquipmentMapper;
import com.lab.mapper.LabMapper;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class EquipmentTransferService {
    private final EquipmentWorkbook workbook;
    private final EquipmentMapper equipment;
    private final EquipmentTransferMapper transfers;
    private final LabMapper labs;
    private final EquipmentPageCache cache;
    private final Validator validator;
    // 2核2GB部署限制同一实例的工作簿解析并发，避免多个压缩文件同时消耗堆内存。
    private final Semaphore workbooks = new Semaphore(1);

    public byte[] template() { requireManager(); return workbook.template(); }

    public byte[] export(EquipmentPageQuery query) {
        Long managerId = requireManager();
        if (!workbooks.tryAcquire()) throw busy();
        try {
            List<Equipment> rows = transfers.selectForExport(query, managerId);
            if (rows.size() > 2000) throw new BusinessException(ResultCode.BAD_REQUEST, "导出最多2000条，请缩小筛选范围");
            return workbook.write(rows);
        } finally { workbooks.release(); }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        Long managerId = requireManager();
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择.xlsx设备模板文件");
        }
        if (file.getSize() > EquipmentWorkbook.MAX_FILE_BYTES) throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "设备文件最大2 MiB");
        if (!workbooks.tryAcquire()) throw busy();
        try {
            List<EquipmentRow> rows = workbook.read(readLimited(file));
            Set<String> normalizedCodes = new HashSet<>();
            List<String> codes = new ArrayList<>();
            SortedSet<Long> labIds = new TreeSet<>();
            for (EquipmentRow row : rows) {
                // 文件不控制状态，统一新增为可用，后续状态由业务流程维护。
                row.setStatus(0);
                Set<ConstraintViolation<EquipmentRow>> errors = validator.validate(row);
                if (!errors.isEmpty()) throw new BusinessException(ResultCode.BAD_REQUEST,
                        "第" + row.getRowNumber() + "行：" + errors.iterator().next().getMessage());
                String code = row.getEquipmentCode().trim();
                String normalized = Normalizer.normalize(code, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
                if (!normalizedCodes.add(normalized)) throw new BusinessException(ResultCode.CONFLICT, "文件内设备编号重复，请检查后重新导入");
                row.setEquipmentCode(code); codes.add(code); labIds.add(row.getLabId());
            }
            // 与设备CRUD保持相同的实验室锁顺序，全部权限与编号检查完成前不插入任何设备。
            for (Long id : labIds) {
                Lab lab = labs.selectByIdForUpdate(id);
                if (lab == null) throw new BusinessException(ResultCode.BAD_REQUEST, "导入数据包含不存在的实验室");
                if (managerId != null && !Objects.equals(managerId, lab.getAdminId())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "只能导入本人负责实验室的设备");
                }
            }
            if (!transfers.findExistingCodes(codes).isEmpty()) throw new BusinessException(ResultCode.CONFLICT, "设备编号已经使用，不能覆盖或重复导入");
            for (EquipmentRow row : rows) {
                Equipment target = new Equipment();
                target.setLabId(row.getLabId()); target.setEquipmentCode(row.getEquipmentCode()); target.setName(row.getName().trim());
                target.setModel(row.getModel()); target.setManufacturer(row.getManufacturer()); target.setPurchaseDate(row.getPurchaseDate());
                target.setDescription(row.getDescription()); target.setStatus(0);
                equipment.insert(target);
            }
            // 数据库唯一键处理并发新增的最后防线；任一插入异常均回滚整批，缓存仅在提交后失效。
            cache.evictAfterCommit();
            return new ImportResult(rows.size());
        } finally { workbooks.release(); }
    }

    private byte[] readLimited(MultipartFile file) {
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > EquipmentWorkbook.MAX_FILE_BYTES) throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "设备文件最大2 MiB");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (IOException ex) { throw new BusinessException(ResultCode.BAD_REQUEST, "无法读取上传文件"); }
    }

    private Long requireManager() {
        StpUtil.checkLogin();
        if (StpUtil.hasRole("admin")) return null;
        StpUtil.checkRole("lab_admin"); return StpUtil.getLoginIdAsLong();
    }
    private BusinessException busy() { return new BusinessException(ResultCode.TOO_MANY_REQUESTS, "正在处理设备表格，请稍后重试"); }
    @Value public static class ImportResult { int importedCount; }
}
