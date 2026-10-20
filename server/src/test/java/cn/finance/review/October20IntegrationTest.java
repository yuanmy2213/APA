package cn.finance.review;

import java.util.*;
import java.io.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static cn.finance.review.Db.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:oct20;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","audit.storage=${java.io.tmpdir}/apa-oct20-tests","audit.demo=true","audit.integration.token=synthetic-integration-test-token-20261020","audit.integration.products=P06,P07"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class October20IntegrationTest {
 static final String BASE="/api/integration/v1",TOKEN="Bearer synthetic-integration-test-token-20261020";
 final Authentication aa=new UsernamePasswordAuthenticationToken("aa",null,List.of(new SimpleGrantedAuthority("ROLE_AA")));
 @MockitoSpyBean Db db;
 @Autowired MockMvc mvc;@Autowired IngestionService ingestion;@Autowired PublicationService pubs;@Autowired ReviewService review;@Autowired QueryService queries;@Autowired WorkbookCodec codec;
 static Map<String,Object> auth(String id){return map("employeeId","L00566785","permissionName","测试角色","authorizationId",id,"application","应用A","appId","APP-A","tenantId","TENANT-A");}
 static Map<String,Object> point(String id){return map("permissionCombinationId",id,"roleName","来源角色","roleType","系统管理权限","setName","业务权限集","pointName","原权限点名称","pointId","0000007","appId","APP-A","tenantId","TENANT-A");}
 static Map<String,Object> matrix(String id){return map("permissionCombinationId",id,"ruleCode","8a","risk","高","violation","风险原因原值：角色包含业务操作，保持原样");}
 byte[] workbook(Map<String,List<Map<String,Object>>> data)throws Exception{
  try(Workbook book=new XSSFWorkbook();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   for(var entry:data.entrySet()){var sheet=book.createSheet(WorkbookCodec.SHEETS.get(entry.getKey()));var cols=Fields.columns(entry.getKey());var header=sheet.createRow(0);for(int i=0;i<cols.size();i++)header.createCell(i).setCellValue(Fields.label(cols.get(i)));int row=1;
    for(var value:entry.getValue()){var r=sheet.createRow(row++);for(int i=0;i<cols.size();i++)r.createCell(i).setCellValue(str(value,cols.get(i)));}}
   book.write(bytes);return bytes.toByteArray();
  }
 }
 Map<String,Object> finish(String id)throws Exception{Map<String,Object> job=null;for(int i=0;i<1200;i++){ingestion.run();job=ingestion.job(aa,id);if(!List.of("QUEUED","RUNNING").contains(str(job,"status")))return job;Thread.sleep(15);}throw new AssertionError("导入超时："+job);}
 Map<String,Object> upload(String product,Map<String,List<Map<String,Object>>> data)throws Exception{var job=ingestion.upload(aa,product,new MockMultipartFile("file","report.xlsx","application/octet-stream",workbook(data)),"auto","FULL","FULL","2026-10-20T10:00:00+08:00",id());return finish(str(job,"id"));}
 Map<String,Object> publish(Map<String,Object> job){assertEquals("VALIDATED",job.get("status"),String.valueOf(job));return pubs.publish(aa,str(job,"draftId"),"1",id(),map("reason","测试确认发布","allowEmpty",true));}
 @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> result){return (List<Map<String,Object>>)result.get("rows");}
 Map<String,Object> query(String snapshot,String kind,boolean source,String field,String value){var q=map("snapshotId",snapshot,"productId","P06","kind",kind,"pageSize",100);if(field!=null)q.put("filters",List.of(map("field",field,"op","eq","value",value)));return queries.query(aa,q,source);}
 Map<String,Object> jsonPayload(String combo){return map("schemaVersion","1","mode","auto","rawCoverage","FULL","riskCoverage","FULL","sourceAsOf","2026-10-20T10:00:00+08:00","datasets",List.of(map("type","POINT","rows",List.of(point(combo))),map("type","MATRIX_RISK","rows",List.of(matrix(combo)))));}
 Map<String,Object> postApi(String path,Map<String,Object> body)throws Exception{return db.decode(mvc.perform(post(BASE+path).header("Authorization",TOKEN).header("Idempotency-Key",id()).contentType("application/json").content(db.encode(body))).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());}

 @Test @Order(1) void templateBusinessIdsAndForeignKeyOnlyRisks()throws Exception{
  try(Workbook b=new XSSFWorkbook(new ByteArrayInputStream(codec.template("all")))){for(String type:List.of("POINT","MATRIX_RISK")){boolean found=false;for(Cell c:b.getSheet(WorkbookCodec.SHEETS.get(type)).getRow(0))if(c.getStringCellValue().equals("权限组合ID"))found=true;assertTrue(found);}assertEquals("授权记录ID",Fields.label("authorizationId"));}
  var mr=matrix("000-C2");mr.put("roleName","报告中的名称原值");var ur=map("authorizationId","000-A1","ruleCode","1","risk","高","violation","用户风险原值");
  var data=new LinkedHashMap<String,List<Map<String,Object>>>();data.put("AUTH",List.of(auth("000-A1")));data.put("USER_RISK",List.of(ur));data.put("POINT",List.of(point("000-C1"),point("000-C2")));data.put("MATRIX_RISK",List.of(mr));publish(upload("P06",data));
  String snapshot=str(queries.snapshot(aa),"id");var raw=rows(query(snapshot,"matrix",true,"permissionCombinationId","000-C2"));assertEquals(1,raw.size());assertEquals("0000007",raw.get(0).get("pointId"));
  var risk=rows(query(snapshot,"matrix",false,"permissionCombinationId","000-C2")).get(0);assertEquals("LINKED",risk.get("linkStatus"));assertEquals("COMBINATION_ID",risk.get("linkMethod"));assertEquals("报告中的名称原值",risk.get("roleName"));assertEquals(matrix("000-C2").get("violation"),risk.get("violation"));assertEquals(raw.get(0).get("id"),risk.get("currentSourceId"));
  var user=rows(query(snapshot,"user",false,"authorizationId","000-A1")).get(0);assertEquals("L00566785",user.get("employeeId"));assertEquals("测试角色",user.get("permissionName"));assertNotNull(user.get("currentSourceId"));
 }

 @Test @Order(2) void explicitMissingIdDoesNotFallBackAndLegacyIdIsVisible()throws Exception{
  var wrong=new LinkedHashMap<>(point("not-present"));wrong.putAll(matrix("not-present"));var ur=new LinkedHashMap<>(auth("missing-grant"));ur.putAll(map("ruleCode","1","risk","高","violation","原始原因"));
  publish(upload("P06",Map.of("AUTH",List.of(auth("000-A1")),"USER_RISK",List.of(ur),"POINT",List.of(point("000-C1")),"MATRIX_RISK",List.of(wrong))));String snapshot=str(queries.snapshot(aa),"id");
  var f=rows(query(snapshot,"matrix",false,null,null)).get(0);assertEquals("UNLINKED",f.get("linkStatus"));assertEquals("COMBINATION_ID_NOT_FOUND",f.get("linkReason"));assertNull(f.get("currentSourceId"));assertEquals("AUTHORIZATION_ID_NOT_FOUND",rows(query(snapshot,"user",false,null,null)).get(0).get("linkReason"));
  var legacy=point("");var risk=new LinkedHashMap<>(legacy);risk.putAll(matrix(""));publish(upload("P06",Map.of("POINT",List.of(legacy),"MATRIX_RISK",List.of(risk))));snapshot=str(queries.snapshot(aa),"id");var source=rows(query(snapshot,"matrix",true,null,null)).get(0);assertTrue(str(source,"permissionCombinationId").startsWith("LEGACY-"));f=rows(query(snapshot,"matrix",false,null,null)).get(0);assertEquals(source.get("permissionCombinationId"),f.get("permissionCombinationId"));assertNotNull(f.get("currentSourceId"));
 }

 @Test @Order(3) void duplicateIdHasBusinessValueAndJsonIdsMustBeText()throws Exception{
  var other=point("same-id");other.put("tenantId","other-tenant");var failed=upload("P06",Map.of("POINT",List.of(point("same-id"),other)));assertEquals("FAILED",failed.get("status"));assertTrue(db.encode(failed.get("errors")).contains("权限组合ID=same-id"));assertTrue(db.encode(failed.get("errors")).contains("首次出现"));
  var badPoint=point("1");badPoint.put("permissionCombinationId",1);badPoint.put("unknownField","不允许静默丢弃");var payload=map("schemaVersion","1","rawCoverage","FULL","sourceAsOf","2026-10-20T10:00:00+08:00","datasets",List.of(map("type","POINT","rows",List.of(badPoint))));var j=postApi("/products/P06/imports",payload);failed=finish(str(j,"id"));assertEquals("FAILED",failed.get("status"));assertTrue(db.encode(failed.get("errors")).contains("必须为字符串"));assertTrue(db.encode(failed.get("errors")).contains("字段不受支持"));
 }

 @Test @Order(4) void bearerJsonImportScopeIdempotencyAndPublishGate()throws Exception{
  mvc.perform(post(BASE+"/products/P06/imports").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(post(BASE+"/products/P06/imports").with(httpBasic("aa","aa-demo-2026")).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/me").header("Authorization",TOKEN)).andExpect(status().isUnauthorized());
  mvc.perform(post(BASE+"/products/P01/imports").header("Authorization",TOKEN).header("Idempotency-Key",id()).contentType("application/json").content(db.encode(jsonPayload("scope")))).andExpect(status().isNotFound());
  mvc.perform(post(BASE+"/products/P06/imports").header("Authorization",TOKEN).contentType("application/json").content(db.encode(jsonPayload("header")))).andExpect(status().isUnprocessableEntity());
  String key=id();var payload=jsonPayload("000-API");String body=db.encode(payload);var result=mvc.perform(post(BASE+"/products/P06/imports").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn().getResponse();var j=db.decode(result.getContentAsString());
  var repeat=mvc.perform(post(BASE+"/products/P06/imports").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType("application/json").content(db.encode(new TreeMap<>(payload)))).andExpect(status().isAccepted()).andReturn().getResponse();assertEquals(j.get("id"),db.decode(repeat.getContentAsString()).get("id"));
  mvc.perform(post(BASE+"/products/P06/imports").header("Authorization",TOKEN).header("Idempotency-Key",key).contentType("application/json").content(db.encode(jsonPayload("different")))).andExpect(status().isConflict());j=finish(str(j,"id"));assertEquals("VALIDATED",j.get("status"));
  mvc.perform(post(BASE+"/drafts/"+j.get("draftId")+"/publish").header("Authorization",TOKEN).header("If-Match","1").header("Idempotency-Key",id()).contentType("application/json").content("{\"reason\":\"machine\"}")).andExpect(status().isForbidden());publish(j);
  var snapshot=postApi("/snapshots",Map.of());var q=map("snapshotId",snapshot.get("id"),"productId","P06","kind","matrix","filters",List.of(map("field","permissionCombinationId","op","eq","value","000-API")));assertEquals(1,rows(postApi("/source-records/query",q)).size());
 }

 @Test @Order(5) void businessIdResolvePatchVersionChecksAndAudit()throws Exception{
  publish(upload("P06",Map.of("POINT",List.of(point("000-EDIT")),"MATRIX_RISK",List.of(matrix("000-EDIT")))));
  String oldSnapshot=str(queries.snapshot(aa),"id");var draft=postApi("/products/P06/drafts",map("kind","matrix"));String d=str(draft,"id");mvc.perform(post(BASE+"/drafts/"+d+"/records/resolve").header("Authorization",TOKEN).contentType("application/json").content(db.encode(map("kind","matrix","businessId",1,"source",true)))).andExpect(status().isUnprocessableEntity());var target=postApi("/drafts/"+d+"/records/resolve",map("kind","matrix","source",true,"businessId","000-EDIT"));var source=(Map<String,Object>)target.get("source");String row=str(source,"id");
  String path=BASE+"/drafts/"+d+"/source-records/"+row+"?kind=matrix";String patch=db.encode(map("reason","接口核对权限点名称","changes",map("pointName","修订名称")));
  mvc.perform(patch(path).header("Authorization",TOKEN).header("If-Match","1").contentType("application/json").content(patch)).andExpect(status().isOk()).andExpect(jsonPath("$.affected").value(1));
  mvc.perform(patch(path).header("Authorization",TOKEN).header("If-Match","1").contentType("application/json").content(patch)).andExpect(status().isPreconditionFailed());
  mvc.perform(patch(path).header("Authorization",TOKEN).contentType("application/json").content(patch)).andExpect(status().isPreconditionRequired());
  mvc.perform(patch(path).header("Authorization",TOKEN).header("If-Match","2").contentType("application/json").content(db.encode(map("reason","尝试修改标识","changes",map("permissionCombinationId","other"))))).andExpect(status().isConflict());
  var riskTarget=postApi("/drafts/"+d+"/records/resolve",map("kind","matrix","source",false,"businessId","000-EDIT","ruleCode","8a"));var risk=(Map<String,Object>)riskTarget.get("finding");assertEquals("待复核",risk.get("workflow"));
  mvc.perform(patch(BASE+"/drafts/"+d+"/findings/"+risk.get("id")).header("Authorization",TOKEN).header("If-Match",str(risk,"recordVersion")).contentType("application/json").content(db.encode(map("reason","人工确认误报","changes",map("workflow","已确认误报"))))).andExpect(status().isOk());
  assertEquals(1L,query(oldSnapshot,"matrix",false,null,null).get("total"));pubs.publish(aa,d,"3",id(),map("reason","审核接口修改","allowEmpty",false));String fresh=str(queries.snapshot(aa),"id");assertEquals(0L,query(fresh,"matrix",false,null,null).get("total"));assertEquals(1L,query(oldSnapshot,"matrix",false,null,null).get("total"));assertTrue(db.count("SELECT COUNT(*) FROM audit_event WHERE actor='integration' AND action='EDIT_SOURCE'",Map.of())>0);assertEquals("修订名称",rows(query(fresh,"matrix",true,null,null)).get(0).get("pointName"));
 }

 @Test @Order(6) void machineMultipartAndFullErrorsShareTheSamePipeline()throws Exception{
  String csv="权限组合ID,角色名称,权限集名称,权限点ID\nDUP,角色,权限集,00001\nDUP,角色2,权限集,00002\n";var file=new MockMultipartFile("file","points.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var response=mvc.perform(multipart(BASE+"/products/P06/files").file(file).param("sourceAsOf","2026-10-20T10:00:00+08:00").param("rawCoverage","FULL").header("Authorization",TOKEN).header("Idempotency-Key",id())).andExpect(status().isAccepted()).andReturn().getResponse();var j=finish(str(db.decode(response.getContentAsString()),"id"));assertEquals("FAILED",j.get("status"));
  mvc.perform(get(BASE+"/import-jobs/"+j.get("id")+"/errors/page").header("Authorization",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.rows[0].businessKey").value(org.hamcrest.Matchers.containsString("DUP")));
 }

 @Test @Order(7) void riskOnlyReplacementRelinksRetainedSourcesByBusinessId()throws Exception{
  publish(upload("P06",Map.of("POINT",List.of(point("000-RETAIN")))));var pending=ingestion.upload(aa,"P06",new MockMultipartFile("file","matrix-risk.csv","text/csv","权限组合ID,检查细项,风险等级\n000-RETAIN,8a,高\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"auto","UNKNOWN","FULL","2026-10-20T10:00:00+08:00",id());var release=publish(finish(str(pending,"id")));String ds=str(release,"matrixDatasetId");var risk=pubs.findings(ds,"matrix").get(0);assertEquals("LINKED",risk.get("linkStatus"));assertEquals("COMBINATION_ID",risk.get("linkMethod"));assertNotNull(risk.get("auditedSourceId"));assertEquals("来源角色",risk.get("roleName"));assertEquals(1,db.count("SELECT COUNT(*) FROM source_point WHERE dataset_id=:d",map("d",ds)));
  var userCsv=ingestion.upload(aa,"P06",new MockMultipartFile("file","user-risk.csv","text/csv","授权记录ID,检查项,风险等级\n000-A1,1,高\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"auto","UNKNOWN","FULL","2026-10-20T10:00:00+08:00",id());assertEquals("VALIDATED",finish(str(userCsv,"id")).get("status"));
  var legacy=auth("");var finding=new LinkedHashMap<>(legacy);finding.putAll(map("ruleCode","1","risk","高","violation","补齐ID时保留风险原因"));publish(upload("P06",Map.of("AUTH",List.of(legacy),"USER_RISK",List.of(finding))));String snapshot=str(queries.snapshot(aa),"id");var original=rows(query(snapshot,"user",false,null,null)).get(0);var draft=pubs.createDraft(aa,"P06",List.of("user"));var target=review.findDraftRecord(aa,str(draft,"id"),str(original,"id"),"user",false);var raw=(Map<String,Object>)target.get("source");review.editSource(aa,str(draft,"id"),str(raw,"id"),"user","1",map("reason","补齐缺失的授权记录ID","changes",map("authorizationId","000-NEW")));var enriched=pubs.findings(str(draft,"userDatasetId"),"user").get(0);assertEquals("000-NEW",enriched.get("authorizationId"));assertEquals("补齐ID时保留风险原因",enriched.get("violation"));assertEquals("",str(queries.source("user",str(enriched,"auditedSourceId")),"authorizationId"));
 }

 @Test @Order(8) void cloningAndPageQueriesAvoidPerRowDatabaseRoundTrips()throws Exception{
  List<Map<String,Object>> auth=new ArrayList<>(),points=new ArrayList<>(),users=new ArrayList<>(),matrix=new ArrayList<>();for(int i=0;i<2000;i++){auth.add(auth("G"+i));var p=point("C"+i);p.put("pointId","P"+i);points.add(p);if(i<500){users.add(map("authorizationId","G"+i,"ruleCode","1","risk","高"));matrix.add(matrix("C"+i));}}
  publish(upload("P06",Map.of("AUTH",auth,"USER_RISK",users,"POINT",points,"MATRIX_RISK",matrix)));clearInvocations(db);long started=System.nanoTime();var draft=pubs.createDraft(aa,"P06",List.of("user","matrix"));double ms=(System.nanoTime()-started)/1_000_000d;
  Set<String> tables=Set.of("source_auth","source_point","risk_finding","user_role_finding","matrix_finding");long individual=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("insert")&&tables.contains(String.valueOf(i.getArguments()[0]))).count();long batches=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("insertBatch")&&tables.contains(String.valueOf(i.getArguments()[0]))).count();assertEquals(0,individual);assertTrue(batches>0&&batches<=20,"Batch calls: "+batches);assertEquals(2000,db.count("SELECT COUNT(*) FROM source_auth WHERE dataset_id=:d",map("d",draft.get("userDatasetId"))));
  String snapshot=str(queries.snapshot(aa),"id");var q=map("snapshotId",snapshot,"productId","P06","kind","user","pageSize",1);clearInvocations(db);queries.query(aa,q,true);long one=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("rows")).count();q.put("pageSize",100);clearInvocations(db);var page=queries.query(aa,q,true);long hundred=mockingDetails(db).getInvocations().stream().filter(i->i.getMethod().getName().equals("rows")).count();assertEquals(100,rows(page).size());assertTrue(hundred<=one+2,"Queries scale per row: "+one+" -> "+hundred);
  System.out.println("EDIT_PERFORMANCE: 4000 sources + 1000 findings; individualInserts="+individual+" batchCalls="+batches+" cloneMs="+ms+" pageQueryCalls="+one+"/"+hundred);
 }
}
