package cn.finance.review;
import java.util.*;
import java.time.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import static cn.finance.review.Db.*;
import static cn.finance.review.ApiError.require;
@Service
public class IngestionService {
 final Db db;final Access access;final FileStore files;final WorkbookCodec codec;final PublicationService publications;final ImportErrorReport errorReports;
 public IngestionService(Db db,Access access,FileStore files,WorkbookCodec codec,PublicationService publications,ImportErrorReport errorReports){this.db=db;this.access=access;this.files=files;this.codec=codec;this.publications=publications;this.errorReports=errorReports;}
 public Map<String,Object> upload(Authentication a,String product,MultipartFile file,String mode,String rawCoverage,String riskCoverage,String asOf,String requestKey)throws Exception{
  access.product(a,product,"write");require(List.of("auto","user","matrix").contains(mode),422,"MODE","上传模式无效");for(String c:List.of(rawCoverage,riskCoverage))require(List.of("FULL","PARTIAL","UNKNOWN").contains(c),422,"COVERAGE","覆盖范围无效");try{OffsetDateTime.parse(asOf);}catch(Exception e){throw new ApiError(422,"SOURCE_TIME","请提供含时区的来源时间");}require(requestKey!=null&&requestKey.length()>=8&&requestKey.length()<=100,422,"IDEMPOTENCY_KEY","上传必须携带幂等键");
  Map<String,Object> asset=files.save(file);String digest=Fields.hash(str(asset,"sha256")+mode+rawCoverage+riskCoverage+asOf);String actor=access.actor(a);
  var prior=db.optional("SELECT * FROM import_job WHERE created_by=:a AND product_id=:p AND request_key=:k",Map.of("a",actor,"p",product,"k",requestKey));
  if(prior!=null){java.nio.file.Files.deleteIfExists(files.path(str(asset,"storageName")));require(digest.equals(str(prior,"requestDigest")),409,"IDEMPOTENCY_CONFLICT","同一幂等键对应不同上传内容");return prior;}
  return db.transaction(()->{asset.put("productId",product);asset.put("createdBy",actor);asset.put("createdAt",now());db.insert("file_asset",asset);var pub=publications.publication(product);var job=map("id",id(),"productId",product,"assetId",asset.get("id"),"draftId",null,"baseReleaseId",pub.get("releaseId"),"baseRevision",pub.get("revision"),"mode",mode,"rawCoverage",rawCoverage,"riskCoverage",riskCoverage,"sourceAsOf",asOf,"status","QUEUED","progress",0,"errors",null,"preview",null,"createdBy",actor,"createdAt",now(),"leaseUntil",null,"leaseOwner",null,"attempts",0,"requestKey",requestKey,"requestDigest",digest);db.insert("import_job",job);db.event(product,actor,"UPLOAD",str(job,"id"),null,map("file",asset.get("originalName"),"sha256",asset.get("sha256")),"上传审查数据，等待校验");return job;});
 }
 public Map<String,Object> job(Authentication a,String id){var j=db.one("SELECT j.*, a.original_name FROM import_job j JOIN file_asset a ON j.asset_id=a.id WHERE j.id=:id",Map.of("id",id));access.product(a,str(j,"productId"),"read");if(j.get("errors")!=null)j.put("errors",db.decode(str(j,"errors")));if(j.get("preview")!=null)j.put("preview",db.decode(str(j,"preview")));return j;}
 public List<Map<String,Object>> list(Authentication a){var scope=access.scope(a);if(scope.isEmpty())return List.of();
  try{return db.rows("SELECT j.id,j.product_id,j.asset_id,j.draft_id,j.status,j.progress,j.phase,j.current_sheet,j.processed_rows,j.completed_sheets,j.created_at,COALESCE(a.original_name,'原文件不可用') AS original_name FROM import_job j LEFT JOIN file_asset a ON j.asset_id=a.id WHERE j.product_id IN (:ids) ORDER BY j.created_at DESC,j.id DESC LIMIT :limit",map("ids",scope,"limit",100));}
  catch(org.springframework.dao.DataAccessException e){String request=id();org.slf4j.LoggerFactory.getLogger(IngestionService.class).error("import list {} failed",request,e);throw new ApiError(503,"IMPORT_LIST_UNAVAILABLE","导入任务列表读取失败，请稍后重试。错误编号："+request);}
 }
 public Map<String,Object> errors(Authentication a,String job,int page,int size){return errorReports.page(job(a,job),page,size);}
 @Scheduled(fixedDelay=700)
 public void run(){String lease=id(),time=now();var candidates=db.rows("SELECT * FROM import_job WHERE status='QUEUED' OR (status='RUNNING' AND lease_until<:now) ORDER BY created_at LIMIT 1",Map.of("now",time));if(candidates.isEmpty())return;var j=candidates.get(0);
  int won=db.update("UPDATE import_job SET status='RUNNING',phase='PARSING',progress=5,lease_owner=:l,lease_until=:until,attempts=attempts+1 WHERE id=:id AND (status='QUEUED' OR (status='RUNNING' AND lease_until<:now))",map("l",lease,"until",Instant.now().plusSeconds(300).toString(),"id",j.get("id"),"now",time));if(won==0)return;
  try(var report=errorReports.open(str(j,"id"),lease)){
   try{
    var asset=db.one("SELECT * FROM file_asset WHERE id=:id",Map.of("id",j.get("assetId")));String productName=str(db.one("SELECT * FROM product WHERE id=:id",Map.of("id",j.get("productId"))),"name");
    var parsed=codec.parse(files.path(str(asset,"storageName")),str(j,"mode"),productName,progress->{var params=new LinkedHashMap<String,Object>(progress);params.putAll(map("id",j.get("id"),"lease",lease,"until",Instant.now().plusSeconds(300).toString()));db.update("UPDATE import_job SET progress=25,phase=:phase,current_sheet=:currentSheet,processed_rows=:processedRows,completed_sheets=:completedSheets,lease_until=:until WHERE id=:id AND lease_owner=:lease AND status='RUNNING'",params);},report::add);
    if(parsed.errorCount>0){fail(j,lease,report.metadata(),map("structure",parsed.structure,"ignoredSheets",parsed.ignored));return;}
    db.update("UPDATE import_job SET phase='STAGING',progress=55,processed_rows=:rows,completed_sheets=:s WHERE id=:id AND lease_owner=:lease",map("rows",parsed.processedRows,"s",parsed.completedSheets,"id",j.get("id"),"lease",lease));
    db.transaction(()->{
     var owned=db.one("SELECT * FROM import_job WHERE id=:id FOR UPDATE",Map.of("id",j.get("id")));require(lease.equals(str(owned,"leaseOwner"))&&str(owned,"status").equals("RUNNING"),409,"LEASE_LOST","任务已由其他实例恢复");
     List<String> chains=new ArrayList<>();if(parsed.data.containsKey("AUTH")||parsed.data.containsKey("USER_RISK"))chains.add("user");if(parsed.data.containsKey("POINT")||parsed.data.containsKey("MATRIX_RISK"))chains.add("matrix");
     var draft=publications.newDraft(str(j,"productId"),map("releaseId",j.get("baseReleaseId"),"revision",j.get("baseRevision")),str(j,"createdBy"),chains,parsed.data.keySet());
     List<Map<String,Object>> summary=new ArrayList<>();
     for(String chain:chains){String ds=str(draft,chain+"DatasetId");String sourceType=chain.equals("user")?"AUTH":"POINT",riskType=chain.equals("user")?"USER_RISK":"MATRIX_RISK";boolean raw=parsed.data.containsKey(sourceType),risk=parsed.data.containsKey(riskType);
      if(raw){
       db.update("DELETE FROM "+Fields.sourceTable(chain)+" WHERE dataset_id=:id",Map.of("id",ds));List<Map<String,Object>> batch=new ArrayList<>();
       for(var row:parsed.data.get(sourceType)){var r=Fields.select(row,Fields.sourceColumns(chain));String rowId=id();r.putAll(map("id",rowId,"datasetId",ds,"recordKey",Fields.storageKey(row,chain,rowId),"sourceAssetId",j.get("assetId"),"sourceSheet",row.get("sourceSheet"),"sourceRow",row.get("sourceRow"),"baseRowId",null,"recordVersion",1));if(chain.equals("user"))r.put("userKey",Fields.identity(r));batch.add(r);if(batch.size()==500){db.insertBatch(Fields.sourceTable(chain),batch);batch.clear();}}
       db.insertBatch(Fields.sourceTable(chain),batch);db.update("UPDATE review_dataset SET raw_coverage=:c,raw_as_of=:t WHERE id=:id",map("c",j.get("rawCoverage"),"t",j.get("sourceAsOf"),"id",ds));
      }
      if(risk){db.update("DELETE FROM "+Fields.detailTable(chain)+" WHERE finding_id IN (SELECT id FROM risk_finding WHERE dataset_id=:id)",Map.of("id",ds));db.update("DELETE FROM risk_finding WHERE dataset_id=:id",Map.of("id",ds));List<Map<String,Object>> riskBatch=new ArrayList<>();for(var row:parsed.data.get(riskType)){var f=new LinkedHashMap<>(row);f.putAll(map("id",id(),"datasetId",ds,"chain",chain,"auditedSourceId",null,"currentSourceId",null,"linkStatus","UNLINKED","linkReason","NO_PAIRED_SOURCE","linkCandidates",0,"baseFindingId",null,"sourceAssetId",j.get("assetId"),"recordVersion",1));riskBatch.add(f);if(riskBatch.size()==500){publications.insertFindings(riskBatch,chain);riskBatch.clear();}}publications.insertFindings(riskBatch,chain);db.update("UPDATE review_dataset SET risk_coverage=:c,risk_as_of=:t WHERE id=:id",map("c",j.get("riskCoverage"),"t",j.get("sourceAsOf"),"id",ds));}
      if(raw||risk)relink(ds,chain,risk);
      db.update("UPDATE review_dataset SET source_as_of=:t,asset_id=:a WHERE id=:id",map("t",j.get("sourceAsOf"),"a",j.get("assetId"),"id",ds));
      summary.add(map("chain",chain,"rawReplaced",raw,"riskReplaced",risk,"rawCount",raw?parsed.data.get(sourceType).size():null,"riskCount",risk?parsed.data.get(riskType).size():null,"links",linkSummary(ds),"rows",risk?parsed.data.get(riskType).stream().limit(3).toList():raw?parsed.data.get(sourceType).stream().limit(3).toList():List.of()));
     }
     db.update("UPDATE import_job SET status='VALIDATED',phase='DONE',progress=100,draft_id=:d,preview=:p WHERE id=:id AND lease_owner=:l",map("d",draft.get("id"),"p",db.encode(map("changes",summary,"ignoredSheets",parsed.ignored,"structure",parsed.structure)),"id",j.get("id"),"l",lease));return null;
    });
   }catch(Exception e){org.slf4j.LoggerFactory.getLogger(IngestionService.class).error("import {} failed",j.get("id"),e);report.add(map("sheet","","row",0,"message",e instanceof ApiError||e instanceof IllegalArgumentException?e.getMessage():"文件解析或暂存失败，请检查格式、数据限制后重试","code",e instanceof ApiError error?error.code:"IMPORT_FAILED"));fail(j,lease,report.metadata(),Map.of());}
  }catch(Exception e){org.slf4j.LoggerFactory.getLogger(IngestionService.class).error("error report for import {} failed",j.get("id"),e);db.update("UPDATE import_job SET status='FAILED',phase='FAILED',progress=100,errors=:e WHERE id=:id AND lease_owner=:l AND status='RUNNING'",map("e",db.encode(map("count",1,"items",List.of(map("sheet","","row",0,"message","错误明细文件保存失败，请检查共享存储并重试")),"complete",false)),"id",j.get("id"),"l",lease));}
 }
 private void fail(Map<String,Object> job,String lease,Map<String,Object> errors,Map<String,Object> preview){db.update("UPDATE import_job SET status='FAILED',phase='FAILED',progress=100,errors=:e,preview=:p WHERE id=:id AND lease_owner=:l AND status='RUNNING'",map("e",db.encode(errors),"p",db.encode(preview),"id",job.get("id"),"l",lease));}
 public List<Map<String,Object>> linkSummary(String dataset){return db.rows("SELECT link_status,COALESCE(link_reason,'LEGACY_NOT_DIAGNOSED') AS link_reason,COUNT(*) AS count FROM risk_finding WHERE dataset_id=:d GROUP BY link_status,link_reason ORDER BY link_status,link_reason",map("d",dataset));}
 void relink(String dataset,String chain,boolean newRisk){
  var sources=db.rows("SELECT * FROM "+Fields.sourceTable(chain)+" WHERE dataset_id=:id",Map.of("id",dataset));
  var linker=new FindingLinker(sources,chain);Map<String,Map<String,Object>> sourceById=new HashMap<>();for(var source:sources)sourceById.put(str(source,"id"),source);
  List<Map<String,Object>> links=new ArrayList<>(),ids=new ArrayList<>();
  for(var finding:publications.findings(dataset,chain)){var match=linker.match(finding);String workflow=newRisk?str(finding,"workflow"):"待复核";
   links.add(map("current",match.sourceId(),"audited",newRisk?match.sourceId():finding.get("auditedSourceId"),"status",match.status(),"method",match.method(),"reason",match.reason(),"detail",db.encode(match.detail()),"candidates",match.candidates(),"workflow",workflow,"id",finding.get("id")));
   if(match.sourceId()!=null){var source=sourceById.get(match.sourceId());var enriched=Fields.select(finding,Fields.detailColumns(chain));for(String key:Fields.detailColumns(chain))if(str(finding,key).isBlank()&&!str(source,key).isBlank())enriched.put(key,source.get(key));enriched.put("id",finding.get("id"));ids.add(enriched);}
   if(links.size()==500){writeLinks(chain,links,ids);links.clear();ids.clear();}
  }
  writeLinks(chain,links,ids);
 }
 private void writeLinks(String chain,List<Map<String,Object>> links,List<Map<String,Object>> ids){db.updateBatch("UPDATE risk_finding SET current_source_id=:current,audited_source_id=:audited,link_status=:status,link_method=:method,link_reason=:reason,link_detail=:detail,link_candidates=:candidates,workflow=:workflow WHERE id=:id",links);db.updateBatch("UPDATE "+Fields.detailTable(chain)+" SET "+String.join(",",Fields.detailColumns(chain).stream().map(k->snake(k)+"=:"+k).toList())+" WHERE finding_id=:id",ids);}
}
