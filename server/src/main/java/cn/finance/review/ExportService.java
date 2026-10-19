package cn.finance.review;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import static cn.finance.review.Db.*;
@Service
public class ExportService {
 final Db db;final Access access;final QueryService queries;final FileStore files;
 public ExportService(Db db,Access access,QueryService queries,FileStore files){this.db=db;this.access=access;this.queries=queries;this.files=files;}
 public Map<String,Object> create(Authentication a,Map<String,Object> query){return create(a,query,id());}
 public Map<String,Object> create(Authentication a,Map<String,Object> query,String requestKey){ApiError.require(requestKey!=null&&requestKey.length()>=8&&requestKey.length()<=100,422,"IDEMPOTENCY_KEY","导出必须携带幂等键");String product=str(query,"productId");if(!product.isBlank()&&!product.equals("all"))access.product(a,product,"export");else for(String p:access.scope(a))access.product(a,p,"export");boolean source=Boolean.TRUE.equals(query.get("source"));queries.build(a,query,source);String digest=Fields.hash(db.encode(new TreeMap<>(query)));var existing=db.optional("SELECT * FROM export_job WHERE created_by=:a AND request_key=:k",Map.of("a",access.actor(a),"k",requestKey));if(existing!=null){ApiError.require(digest.equals(str(existing,"requestDigest")),409,"IDEMPOTENCY_CONFLICT","同一幂等键对应不同导出条件");return existing;}var job=map("id",id(),"snapshotId",query.get("snapshotId"),"productId",product.isBlank()||product.equals("all")?null:product,"chain",query.get("kind"),"queryJson",db.encode(query),"status","QUEUED","storageName",null,"errors",null,"createdBy",access.actor(a),"createdAt",now(),"leaseUntil",null,"leaseOwner",null,"requestKey",requestKey,"requestDigest",digest);db.insert("export_job",job);for(String p:access.scope(a))if(product.isBlank()||product.equals("all")||p.equals(product))db.event(p,access.actor(a),"REQUEST_EXPORT",str(job,"id"),null,map("snapshotId",query.get("snapshotId")),"按当前快照和筛选导出");return job;}
 public Map<String,Object> get(Authentication a,String id){var j=db.one("SELECT * FROM export_job WHERE id=:id",Map.of("id",id));ApiError.require(access.actor(a).equals(str(j,"createdBy")),404,"NOT_FOUND","导出任务不存在或无访问权限");queries.snapshotMeta(a,str(j,"snapshotId"));String p=str(j,"productId");if(!p.isBlank())access.product(a,p,"export");return j;}
 @Scheduled(fixedDelay=1000)
 public void run(){var jobs=db.rows("SELECT * FROM export_job WHERE status='QUEUED' OR (status='RUNNING' AND lease_until<:now) ORDER BY created_at LIMIT 1",Map.of("now",now()));if(jobs.isEmpty())return;var j=jobs.get(0);String lease=id();int won=db.update("UPDATE export_job SET status='RUNNING',lease_owner=:l,lease_until=:u WHERE id=:id AND (status='QUEUED' OR (status='RUNNING' AND lease_until<:now))",map("l",lease,"u",Instant.now().plusSeconds(300).toString(),"id",j.get("id"),"now",now()));if(won==0)return;String name=str(j,"id")+".xlsx";
  try {Authentication a=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(str(j,"createdBy"),"",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+str(j,"createdBy").toUpperCase(Locale.ROOT))));Map<String,Object> q=db.decode(str(j,"queryJson"));boolean source=Boolean.TRUE.equals(q.get("source"));String chain=str(q,"kind");List<String> kinds=Boolean.TRUE.equals(q.get("allKinds"))?List.of("user","matrix"):List.of(chain);try(SXSSFWorkbook book=new SXSSFWorkbook(100);OutputStream out=Files.newOutputStream(files.path(name))){book.setCompressTempFiles(true);
   for(String k:kinds){Map<String,Object> scoped=new LinkedHashMap<>(q);scoped.put("kind",k);var b=queries.build(a,scoped,source);List<String> fields=new ArrayList<>();if(source)fields.addAll(Fields.sourceColumns(k));else{fields.addAll(Fields.COMMON);fields.addAll(Fields.detailColumns(k));}fields.addAll(List.of("sourceAssetId","sourceSheet","sourceRow"));Sheet s=book.createSheet(k.equals("user")?(source?"原始授权记录":"用户角色风险"):(source?"原始权限点":"权限矩阵风险"));Row h=s.createRow(0);for(int i=0;i<fields.size();i++){h.createCell(i).setCellValue(Fields.label(fields.get(i)).replace("风险明细说明",k.equals("matrix")?"风险原因":"风险明细说明"));s.setColumnWidth(i,22*256);}s.createFreezePane(0,1);final int[] index={1};String projection=source?"f.*":"f.*,d.*";
    db.jdbc.query("SELECT "+projection+b.from()+b.where()+" ORDER BY "+b.order(),b.parameters(),(org.springframework.jdbc.core.RowCallbackHandler)rs->{Row row=s.createRow(index[0]++);for(int i=0;i<fields.size();i++){Object value=rs.getObject(snake(fields.get(i)));if(value instanceof Number n)row.createCell(i).setCellValue(n.doubleValue());else row.createCell(i).setCellValue(value==null?"":String.valueOf(value));}if(index[0]%1000==0)db.update("UPDATE export_job SET lease_until=:u WHERE id=:id AND lease_owner=:l",map("u",Instant.now().plusSeconds(300).toString(),"id",j.get("id"),"l",lease));});
   }
   Sheet meta=book.createSheet("导出说明");String[] notes={"快照ID："+str(j,"snapshotId"),"产品范围："+str(q,"productId"),"导出筛选："+db.encode(q),"原始记录副本修订不等于已完成生产权限整改。","置信度为来源报告提供，不等同于风险发生概率。"};for(int i=0;i<notes.length;i++)meta.createRow(i).createCell(0).setCellValue(notes[i]);book.write(out);}
   db.update("UPDATE export_job SET status='SUCCEEDED',storage_name=:n WHERE id=:id AND lease_owner=:l",map("n",name,"id",j.get("id"),"l",lease));
  }catch(Exception e){try{Files.deleteIfExists(files.path(name));}catch(Exception ignored){}db.update("UPDATE export_job SET status='FAILED',errors='导出失败，请刷新快照后重试' WHERE id=:id AND lease_owner=:l",map("id",j.get("id"),"l",lease));}
 }
}
