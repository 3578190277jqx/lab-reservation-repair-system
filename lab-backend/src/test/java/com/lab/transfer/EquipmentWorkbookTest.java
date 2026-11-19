package com.lab.transfer;

import com.lab.common.BusinessException;
import com.lab.entity.Equipment;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentWorkbookTest {
    private final EquipmentWorkbook codec = new EquipmentWorkbook();
    private byte[] workbook(String code, String lab, boolean formula, int rows) throws Exception {
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("设备");
            String[] headers={"设备编号","设备名称","实验室ID","型号","制造商","采购日期","设备说明"};
            Row header=sheet.createRow(0); for(int i=0;i<headers.length;i++)header.createCell(i);
            for(int i=0;i<headers.length;i++)sheet.getRow(0).getCell(i).setCellValue(headers[i]);
            for(int n=0;n<rows;n++) {
                Row row=sheet.createRow(n+1);row.createCell(0).setCellValue(code+(rows==1?"":n));
                row.createCell(1).setCellValue("数字示波器");row.createCell(2).setCellValue(lab);
                if(formula)row.createCell(3).setCellFormula("1+1");
                row.createCell(5).setCellValue("2026-10-01");
            }
            book.write(out);return out.toByteArray();
        }
    }
    @Test void importsAValidWorkbookAndKeepsCodesAsText() throws Exception {
        EquipmentRow row=codec.read(workbook("0001","3",false,1)).get(0);
        assertEquals("0001",row.getEquipmentCode());assertEquals(3L,row.getLabId());
        assertEquals("数字示波器",row.getName());assertEquals(0,row.getStatus());
        assertEquals("2026-10-01",row.getPurchaseDate().toString());
    }
    @Test void rejectsFormulaInsteadOfUsingItsCachedValue() throws Exception {
        assertThrows(BusinessException.class,()->codec.read(workbook("EQ-1","3",true,1)));
    }
    @Test void rejectsMoreThan500RowsBeforeAnyDatabaseWrite() throws Exception {
        assertThrows(BusinessException.class,()->codec.read(workbook("EQ-","3",false,501)));
    }
    @Test void rejectsMissingOrInvalidLabAndEmptyWorkbook() throws Exception {
        assertThrows(BusinessException.class,()->codec.read(workbook("EQ-1","",false,1)));
        assertThrows(BusinessException.class,()->codec.read(workbook("EQ-1","1.5",false,1)));
        assertThrows(BusinessException.class,()->codec.read(workbook("EQ-1","3",false,0)));
    }
    @Test void rejectsWrongHeaderAndFakeZip() throws Exception {
        byte[] data=workbook("EQ-1","3",false,1);
        try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(data));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            book.getSheetAt(0).getRow(0).getCell(0).setCellValue("status");book.write(out);
            assertThrows(BusinessException.class,()->codec.read(out.toByteArray()));
        }
        assertThrows(BusinessException.class,()->codec.read(new byte[]{1,2,3}));
    }
    @Test void exportsUntrustedTextAsAStringAndProducesAnEditableTemplate() throws Exception {
        Equipment equipment=new Equipment();equipment.setEquipmentCode("=HYPERLINK(\"bad\")");
        equipment.setName("数字示波器");equipment.setLabId(3L);equipment.setStatus(0);
        try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(codec.write(Collections.singletonList(equipment))))){
            assertEquals(CellType.STRING,book.getSheetAt(0).getRow(1).getCell(0).getCellType());
        }
        try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(codec.template()))){
            assertEquals("设备编号",book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(7,book.getSheetAt(0).getRow(0).getLastCellNum());
        }
    }
}

