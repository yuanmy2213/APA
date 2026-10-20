package cn.finance.review;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;
import static cn.finance.review.Db.*;

/** Full error details are durable files; only a preview is kept in the task JSON. */
@Component
public class ImportErrorReport {
 final Db db;final FileStore files;
 public ImportErrorReport(Db db,FileStore files){this.db=db;this.files=files;}
 public Writer open(String job,String lease)throws IOException{return new Writer(job+"-"+lease);}
 public class Writer implements AutoCloseable {
  final String detailsFile,csvFile;final BufferedWriter details,csv;final List<Map<String,Object>> preview=new ArrayList<>();int count;
  Writer(String prefix)throws IOException{detailsFile=prefix+".jsonl";csvFile=prefix+".csv";details=Files.newBufferedWriter(files.path(detailsFile),StandardCharsets.UTF_8);csv=Files.newBufferedWriter(files.path(csvFile),StandardCharsets.UTF_8);csv.write("\uFEFF工作表,行号,问题,首个重复行,业务键,错误编码\r\n");}
  public void add(Map<String,Object> error){try{count++;if(preview.size()<100)preview.add(error);details.write(db.encode(error));details.newLine();String[] keys={"sheet","row","message","firstRow","businessKey","code"};for(int i=0;i<keys.length;i++){if(i>0)csv.write(',');String v=Objects.toString(error.get(keys[i]),"");if(v.matches("(?s)^[=+@-].*"))v="'"+v;csv.write('"');csv.write(v.replace("\"","\"\""));csv.write('"');}csv.write("\r\n");}catch(IOException e){throw new UncheckedIOException(e);}}
  public Map<String,Object> metadata(){try{details.flush();csv.flush();}catch(IOException e){throw new UncheckedIOException(e);}return map("count",count,"items",preview,"previewCount",preview.size(),"hasMore",count>preview.size(),"detailsFile",detailsFile,"csvFile",csvFile,"complete",true);}
  public void close()throws IOException{try{details.close();}finally{csv.close();}}
 }
 @SuppressWarnings("unchecked")
 public Map<String,Object> page(Map<String,Object> job,int page,int size){ApiError.require(page>=1&&size>=1&&size<=200,422,"PAGINATION","错误分页每页支持1至200条");Map<String,Object> meta=job.get("errors") instanceof Map<?,?> m?(Map<String,Object>)m:map("count",0,"items",List.of());List<Map<String,Object>> rows=new ArrayList<>();long offset=(long)(page-1)*size;String file=str(meta,"detailsFile");
  if(!file.isBlank()){try(var lines=Files.lines(files.path(file),StandardCharsets.UTF_8)){lines.skip(offset).limit(size).map(db::decode).forEach(rows::add);}catch(IOException e){throw new ApiError(503,"ERROR_REPORT_UNAVAILABLE","错误明细暂不可读，请重试");}}
  else if(meta.get("items") instanceof List<?> list)for(int i=(int)Math.min(offset,list.size());i<Math.min(offset+size,list.size());i++)rows.add((Map<String,Object>)list.get(i));
  boolean incomplete=!Boolean.TRUE.equals(meta.get("complete"))&&meta.get("count") instanceof Number n&&meta.get("items") instanceof List<?> list&&n.intValue()>list.size();
  return map("rows",rows,"total",meta.getOrDefault("count",0),"page",page,"pageSize",size,"complete",!incomplete);
 }
 @SuppressWarnings("unchecked")
 public Resource csv(Map<String,Object> job){Map<String,Object> meta=job.get("errors") instanceof Map<?,?> m?(Map<String,Object>)m:map("items",List.of());String name=str(meta,"csvFile");if(name.isBlank()&&meta.get("count") instanceof Number n&&meta.get("items") instanceof List<?> items&&n.intValue()>items.size())throw new ApiError(503,"ERROR_REPORT_UNAVAILABLE","完整错误文件未生成，请检查存储后重新上传");if(!name.isBlank()){var resource=new FileSystemResource(files.path(name));ApiError.require(resource.exists(),503,"ERROR_REPORT_UNAVAILABLE","错误导出文件暂不可读");return resource;}
  StringBuilder text=new StringBuilder("\uFEFF工作表,行号,问题\r\n");if(meta.get("items") instanceof List<?> list)for(Object row:list){var r=(Map<String,Object>)row;for(String k:List.of("sheet","row","message")){String value=str(r,k);if(value.matches("(?s)^[=+@-].*"))value="'"+value;text.append('"').append(value.replace("\"","\"\"")).append("\",");}text.setLength(text.length()-1);text.append("\r\n");}return new ByteArrayResource(text.toString().getBytes(StandardCharsets.UTF_8));
 }
}
