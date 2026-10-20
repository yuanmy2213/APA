package cn.finance.review;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.util.XMLHelper;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;
import org.springframework.stereotype.Component;
import static cn.finance.review.Db.*;
@Component
public class WorkbookCodec {
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 public WorkbookCodec(com.fasterxml.jackson.databind.ObjectMapper json){this.json=json;}
 public static final Map<String,String> SHEETS=Map.of("AUTH","原始权限明细数据","USER_RISK","风险记录明细","POINT","原始权限点明细数据","MATRIX_RISK","权限矩阵检查结果");
 public static class Parsed {
  public final Map<String,List<Map<String,Object>>> data=new LinkedHashMap<>();
  public final List<Map<String,Object>> errors=new ArrayList<>(),structure=new ArrayList<>();
  public int errorCount,completedSheets;public long processedRows;public final List<String> ignored=new ArrayList<>();
  java.util.function.Consumer<Map<String,Object>> sink;
  void error(String sheet,int row,String message){error(sheet,row,message,Map.of());}
  void error(String sheet,int row,String message,Map<String,Object> extra){var e=map("sheet",sheet,"row",row,"message",message);e.putAll(extra);errorCount++;if(errors.size()<100)errors.add(e);if(sink!=null)sink.accept(e);}
 }
 static class DateFormatter extends DataFormatter {
  DateFormatter(){super(Locale.ROOT);}
  @Override public String formatRawCellContents(double value,int index,String format,boolean use1904){if(DateUtil.isADateFormat(index,format)&&DateUtil.isValidExcelDate(value))return DateUtil.getLocalDateTime(value,use1904).toLocalDate().toString();return super.formatRawCellContents(value,index,format,use1904);}
 }
 class Collector {
  final Parsed p;final String sheet,mode,product;final java.util.function.Consumer<Map<String,Object>> pulse;String type;Map<Integer,String> headers;int nonEmpty;final Map<String,Integer> unique=new HashMap<>();Collection<String> observed=List.of();
  Collector(Parsed p,String sheet,String mode,String product,java.util.function.Consumer<Map<String,Object>> pulse){this.p=p;this.sheet=sheet;this.mode=mode;this.product=product;this.pulse=pulse;}
  void formula(int row){if(type!=null)p.error(sheet,row,"包含公式单元格；请转为静态值后上传");}
  void row(int row,Map<Integer,String> cells){if(cells.values().stream().allMatch(String::isBlank))return;nonEmpty++;if(nonEmpty%500==0)progress();
   if(headers==null){if(nonEmpty>20)return;if(observed.isEmpty())observed=new ArrayList<>(cells.values());type=detect(sheet,cells.values(),mode);if(type==null)return;headers=new LinkedHashMap<>();cells.forEach((k,v)->{if(!v.isBlank())headers.put(k,v);});if(new HashSet<>(headers.values()).size()!=headers.size())p.error(sheet,row,"表头存在重复列");if(p.data.containsKey(type)){p.error(sheet,row,"同一文件中重复提供了同类核心工作表");return;}p.data.put(type,new ArrayList<>());List<String> req=switch(type){case "AUTH"->List.of("employeeId","permissionName");case "POINT"->List.of("roleName","setName","pointId");case "USER_RISK"->has(new HashSet<>(cells.values()),"authorizationId")?List.of("authorizationId","ruleCode"):List.of("employeeId","permissionName","ruleCode");default->has(new HashSet<>(cells.values()),"permissionCombinationId")?List.of("permissionCombinationId","ruleCode"):List.of("ruleCode","roleName","pointId","setName");};for(String key:req)if(Fields.ALIASES.get(key).stream().noneMatch(cells.values()::contains))p.error(sheet,row,"缺少必填列："+Fields.label(key));return;}
   if(!p.data.containsKey(type))return;p.processedRows++;List<Map<String,Object>> rows=p.data.get(type);int max=type.endsWith("RISK")?20000:100000;if(rows.size()>=max)throw new ApiError(422,"ROW_LIMIT",sheet+"超出"+max+"条非空数据限制，未截断发布");
   Map<String,String> named=new LinkedHashMap<>();headers.forEach((col,label)->named.put(label,cells.getOrDefault(col,"")));
   try {Map<String,Object> r=Fields.normalize(named,type);r.put("sourceSheet",sheet);r.put("sourceRow",row);String pn=str(r,"productName");if(!pn.isBlank()&&!pn.equals(product))p.error(sheet,row,"记录所属产品与本次选择的产品不一致");for(String e:Fields.validate(r,type))p.error(sheet,row,e);
    String chain=type.equals("AUTH")||type.equals("USER_RISK")?"user":"matrix";String key=Fields.key(r,chain);if(type.endsWith("RISK"))key=str(r,"ruleCode")+"|"+key;if(!type.equals("AUTH")||Fields.requiresSourceUniqueness(r,chain)){Integer first=unique.putIfAbsent(key,row);if(first!=null){String business=duplicateKey(r,type);p.error(sheet,row,"重复记录："+business+"；首次出现于第"+first+"行",map("code","DUPLICATE","firstRow",first,"businessKey",business));}}rows.add(r);
   }catch(IllegalArgumentException e){p.error(sheet,row,e.getMessage());}
  }
  void progress(){pulse.accept(map("phase","PARSING","currentSheet",sheet,"processedRows",p.processedRows,"completedSheets",p.completedSheets));}
  void end(){if(headers==null)p.ignored.add(sheet);p.completedSheets++;p.structure.add(map("sheet",sheet,"type",type==null?"IGNORED":type,"headers",headers==null?observed:new ArrayList<>(headers.values()),"rows",headers==null?0:Math.max(0,nonEmpty-1),"message",headers==null?"未识别为核心明细，已忽略；如需导入，请核对标准模板的必填列":"已识别核心数据"));progress();}
 }
 static String duplicateKey(Map<String,Object> row,String type){List<String> keys=type.equals("AUTH")||type.equals("USER_RISK")?List.of("employeeId","permissionName","authorizationId","appId","application","tenantId"):List.of("permissionCombinationId","roleName","setName","pointId","appId","application","tenantId");return String.join("；",keys.stream().filter(k->!str(row,k).isBlank()).map(k->Fields.label(k)+"="+str(row,k)).toList());}
 static String detect(String name,Collection<String> headers,String mode){Set<String> hs=new HashSet<>(headers);boolean employee=has(hs,"employeeId"),permission=has(hs,"permissionName"),point=has(hs,"pointId"),rule=has(hs,"ruleCode");
  if(name.equals("原始权限明细数据")||name.equals("原始授权记录"))return "AUTH";
  if(name.equals("原始权限点明细数据"))return "POINT";
  if(name.equals("风险记录明细")||name.equals("违规记录明细"))return "USER_RISK";
  if(name.equals("权限矩阵检查结果"))return "MATRIX_RISK";
  if(List.of("人员分类","数据概况","汇总","风险等级评估","整改建议","快照","填写说明","统计").stream().anyMatch(name::contains))return null;
  if(point&&name.contains("原始"))return "POINT";if(employee&&permission&&name.contains("原始"))return "AUTH";
  if(rule&&has(hs,"permissionCombinationId"))return "MATRIX_RISK";if(rule&&has(hs,"authorizationId"))return "USER_RISK";if(point&&rule)return "MATRIX_RISK";if(employee&&rule)return "USER_RISK";
  if(point&&has(hs,"roleName")&&has(hs,"setName"))return "POINT";
  if(employee&&permission)return "AUTH";return null;
 }
 static boolean has(Set<String> headers,String key){return Fields.ALIASES.get(key).stream().anyMatch(headers::contains);}
 public Parsed parse(Path path,String mode,String product,Runnable pulse)throws Exception{return parse(path,mode,product,x->pulse.run(),null);}
 public Parsed parse(Path path,String mode,String product,java.util.function.Consumer<Map<String,Object>> pulse,java.util.function.Consumer<Map<String,Object>> errors)throws Exception {
  Parsed p=new Parsed();p.sink=errors;String name=path.toString().toLowerCase(Locale.ROOT);
  if(name.endsWith(".json")){parseJson(path,p,mode,product,pulse);
  }else if(name.endsWith(".xlsx")){
   try(OPCPackage pack=OPCPackage.open(path.toFile(),PackageAccess.READ)){XSSFReader reader=new XSSFReader(pack);var strings=reader.getSharedStringsTable();var styles=reader.getStylesTable();var sheets=(XSSFReader.SheetIterator)reader.getSheetsData();
    while(sheets.hasNext()){try(InputStream in=sheets.next()){Collector c=new Collector(p,sheets.getSheetName(),mode,product,pulse);var handler=new XSSFSheetXMLHandler(styles,strings,new XSSFSheetXMLHandler.SheetContentsHandler(){Map<Integer,String> cells;public void startRow(int row){cells=new LinkedHashMap<>();}public void endRow(int row){c.row(row+1,cells);}public void cell(String ref,String value,XSSFComment comment){if(ref!=null)cells.put(new org.apache.poi.ss.util.CellReference(ref).getCol()+0,value==null?"":value);}public void headerFooter(String text,boolean header,String tag){}},new DateFormatter(),true);
     XMLReader parser=XMLHelper.newXMLReader();XMLFilterImpl filter=new XMLFilterImpl(parser){int row;@Override public void startElement(String uri,String local,String q,Attributes a)throws SAXException{if(local.equals("row")||q.equals("row")){try{row=Integer.parseInt(a.getValue("r"));}catch(Exception ignored){}}if(local.equals("f")||q.equals("f"))c.formula(row);super.startElement(uri,local,q,a);}};filter.setContentHandler(handler);filter.parse(new InputSource(in));c.end();}
    }
   }
  }else if(name.endsWith(".xls")){
   try(Workbook book=new HSSFWorkbook(Files.newInputStream(path))){DataFormatter fmt=new DateFormatter();for(Sheet s:book){Collector c=new Collector(p,s.getSheetName(),mode,product,pulse);for(Row row:s){Map<Integer,String> cells=new LinkedHashMap<>();for(Cell cell:row){if(cell.getCellType()==CellType.FORMULA)c.formula(row.getRowNum()+1);String v=fmt.formatCellValue(cell);if(!v.isBlank())cells.put(cell.getColumnIndex(),v);}c.row(row.getRowNum()+1,cells);}c.end();}}
  }else {
   Collector c=new Collector(p,"CSV",mode,product,pulse);try(PushbackReader in=new PushbackReader(Files.newBufferedReader(path,StandardCharsets.UTF_8),2)){List<String> cells=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false;int ch,row=1;while((ch=in.read())!=-1){char x=(char)ch;if(x=='\uFEFF'&&row==1&&cells.isEmpty()&&value.isEmpty())continue;if(x=='"'){if(quoted){int n=in.read();if(n=='"')value.append('"');else{quoted=false;if(n!=-1)in.unread(n);}}else if(value.isEmpty())quoted=true;else throw new IllegalArgumentException("CSV 引号格式错误");}else if(!quoted&&(x==','||x=='\n'||x=='\r')){cells.add(value.toString());value.setLength(0);if(x!=','){if(x=='\r'){int n=in.read();if(n!='\n'&&n!=-1)in.unread(n);}Map<Integer,String> m=new LinkedHashMap<>();for(int i=0;i<cells.size();i++)m.put(i,cells.get(i));c.row(row++,m);cells.clear();}}else value.append(x);if(value.length()>12000)throw new IllegalArgumentException("CSV 字段过长");}if(quoted)throw new IllegalArgumentException("CSV 引号没有闭合");if(!cells.isEmpty()||!value.isEmpty()){cells.add(value.toString());Map<Integer,String> m=new LinkedHashMap<>();for(int i=0;i<cells.size();i++)m.put(i,cells.get(i));c.row(row,m);}}c.end();
  }
  if(p.data.isEmpty())p.error("",0,"未识别到四类核心数据，请检查表头或使用标准模板");
  for(String type:p.data.keySet()){String chain=type.equals("AUTH")||type.equals("USER_RISK")?"user":"matrix";if(!mode.equals("auto")&&!mode.equals(chain))p.error(SHEETS.get(type),1,"文件包含另一条业务链路，请选择完整报告模式上传");}
  return p;
 }
 private void parseJson(Path path,Parsed p,String mode,String product,java.util.function.Consumer<Map<String,Object>> pulse)throws Exception{
  var root=json.readTree(path.toFile());ApiError.require(root.isObject()&&root.path("schemaVersion").asText().equals("1")&&root.path("datasets").isArray(),422,"JSON_SCHEMA","JSON必须包含schemaVersion=1和datasets数组");
  ApiError.require(root.path("datasets").size()>=1&&root.path("datasets").size()<=4,422,"JSON_DATASETS","每次提供1至4类核心数据");
  for(var dataset:root.path("datasets")){
   String type=dataset.path("type").asText();ApiError.require(SHEETS.containsKey(type)&&dataset.path("rows").isArray(),422,"JSON_DATASET","数据类型或rows数组无效");
   Collector c=new Collector(p,SHEETS.get(type),mode,product,pulse);var columns=Fields.columns(type);Map<Integer,String> header=new LinkedHashMap<>();for(int i=0;i<columns.size();i++)header.put(i,columns.get(i));c.row(1,header);int line=1;
   for(var row:dataset.path("rows")){line++;if(!row.isObject()){p.error(SHEETS.get(type),line,"记录必须为JSON对象");continue;}Set<String> supplied=new HashSet<>();row.fieldNames().forEachRemaining(supplied::add);for(String field:supplied)if(!columns.contains(field))p.error(SHEETS.get(type),line,"字段不受支持："+field);
    String required=type.equals("POINT")||type.equals("MATRIX_RISK")?"permissionCombinationId":type.equals("USER_RISK")?"authorizationId":"";
    if(!required.isBlank()&&row.path(required).asText().isBlank())p.error(SHEETS.get(type),line,Fields.label(required)+"不能为空；JSON接口按明确业务ID关联");
    Map<Integer,String> cells=new LinkedHashMap<>();for(int i=0;i<columns.size();i++){String field=columns.get(i);var value=row.get(field);if(value!=null&&!value.isNull()){if(value.isContainerNode())p.error(SHEETS.get(type),line,Fields.label(field)+"应为单值");if((field.endsWith("Id")||field.equals("employeeId"))&&!value.isTextual())p.error(SHEETS.get(type),line,Fields.label(field)+"必须为字符串，以保留前导0");cells.put(i,value.asText());}}
    // An explicit empty object is still an invalid data record, not a silently skipped blank row.
    if(cells.isEmpty())p.error(SHEETS.get(type),line,"记录不能为空");else c.row(line,cells);
   }
   c.end();
  }
 }
 public byte[] template(String kind)throws Exception{try(Workbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
  List<String> types=kind.equals("user")?List.of("AUTH","USER_RISK"):kind.equals("matrix")?List.of("POINT","MATRIX_RISK"):List.of("AUTH","USER_RISK","POINT","MATRIX_RISK");
  for(String type:types){Sheet s=book.createSheet(SHEETS.get(type));Row h=s.createRow(0);List<String> cols=Fields.columns(type);CellStyle style=book.createCellStyle();style.setDataFormat(book.createDataFormat().getFormat("@"));for(int i=0;i<cols.size();i++){h.createCell(i).setCellValue(Fields.label(cols.get(i)).replace("风险明细说明",type.equals("MATRIX_RISK")?"风险原因":"风险明细说明"));s.setColumnWidth(i,22*256);s.setDefaultColumnStyle(i,style);}s.createFreezePane(0,1);}
  Sheet n=book.createSheet("填写说明");String[] notes={"四类核心数据分别对应原始授权、用户角色风险、原始权限点绑定、矩阵风险。可删除本次不上传的核心工作表，空工作表表示明确的空清单。","上传时选择产品、来源时点、原始数据和风险清单覆盖范围；所有编号按文本填写，保留前导0。","用户角色检查项1至7，矩阵细项8a至8f。检查项10a至10f作为历史别名兼容。","原始权限明细：有授权ID时按来源系统、应用、租户及授权ID校验重复；无授权ID时逐行保留，不以字段组合强制去重。同文件可混合两类记录。","原始权限点明细以权限组合ID为业务主键，同一产品与版本内唯一；矩阵风险使用相同ID关联。不同组合ID可绑定同一权限点。编号按文本填写。", "历史Excel缺少权限组合ID时生成LEGACY-前缀兼容ID；历史风险仅在唯一候选时回填该ID，无法确定时保留未关联诊断。有显式ID但不存在时不会退化为名称匹配。", "人员类别和是否雇员为独立来源字段，未提供时为未知，不从风险列表推断总用户量。","用户角色风险优先按授权记录ID关联。原始授权与风险配对上传时建立审查依据关联。仅上传风险时可先发布，原始总量显示缺失或历史基线。","不执行宏、脚本、公式或Excel超链接。日期为YYYY-MM-DD；置信度为0至1或百分数。","完整空清单发布必须确认替换为0条，0条风险不自动代表全部检查通过。"};for(int i=0;i<notes.length;i++)n.createRow(i).createCell(0).setCellValue(notes[i]);n.setColumnWidth(0,110*256);book.write(out);return out.toByteArray();}}
}
