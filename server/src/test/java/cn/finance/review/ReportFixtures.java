package cn.finance.review;

import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Set the target product when reusing a synthetic workbook across product-scope tests. */
final class ReportFixtures {
 static String product(String id){return Fields.PRODUCT_NAMES[Integer.parseInt(id.substring(1))-1];}
 static byte[] forProduct(byte[] bytes,String product)throws IOException{
  try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes));ByteArrayOutputStream out=new ByteArrayOutputStream()){
   for(Sheet sheet:book){Row header=sheet.getRow(0);if(header==null)continue;for(Cell cell:header)if(cell.toString().equals("产品名称"))for(Row row:sheet)if(row.getRowNum()>0&&row.getPhysicalNumberOfCells()>1)row.createCell(cell.getColumnIndex()).setCellValue(product);}
   book.write(out);return out.toByteArray();
  }
 }
}
