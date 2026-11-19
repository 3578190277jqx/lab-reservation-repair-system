package com.lab.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.dto.LabPageQuery;
import com.lab.dto.LabSaveRequest;
import com.lab.entity.Lab;

/** 实验室查询及管理。 */
public interface LabService extends IService<Lab> {
    Page<Lab> pageLabs(LabPageQuery query);
    Lab getLab(Long id);
    Lab createLab(LabSaveRequest request);
    Lab updateLab(Long id, LabSaveRequest request);
    void deleteLab(Long id);
}
