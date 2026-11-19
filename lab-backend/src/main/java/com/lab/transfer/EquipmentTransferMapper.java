package com.lab.transfer;

import com.lab.dto.EquipmentPageQuery;
import com.lab.entity.Equipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface EquipmentTransferMapper {
    List<Equipment> selectForExport(@Param("query") EquipmentPageQuery query, @Param("managerId") Long managerId);
    /** 不排除deleted=1，资产编号的数据库唯一约束也覆盖逻辑删除记录。 */
    List<String> findExistingCodes(@Param("codes") List<String> codes);
}
