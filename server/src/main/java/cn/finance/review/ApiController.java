package cn.finance.review;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Value;
import static cn.finance.review.Db.*;
@RestController
@RequestMapping("/api")
public class ApiController {
 final Db db;final Access access;final QueryService queries;final PublicationService pubs;final IngestionService ingestion;final ReviewService review;final ExportService exports;final WorkbookCodec codec;final FileStore files;final boolean demo;
 public ApiController(Db db,Access access,QueryService queries,PublicationService pubs,IngestionService ingestion,ReviewService review,ExportService exports,WorkbookCodec codec,FileStore files,@Value("${audit.demo:false}")boolean demo){this.db=db;this.access=access;this.queries=queries;this.pubs=pubs;this.ingestion=ingestion;this.review=review;this.exports=exports;this.codec=codec;this.files=files;this.demo=demo;}
 @GetMapping("/me") public Object me(Authentication a){return access.me(a,demo);}
 @GetMapping("/csrf") public Object csrf(CsrfToken token){return map("token",token.getToken(),"headerName",token.getHeaderName());}
 @PostMapping("/snapshots") public Object snapshot(Authentication a){return queries.snapshot(a);}
 @GetMapping("/dashboard") public Object dashboard(Authentication a,@RequestParam String snapshotId){return queries.dashboard(a,snapshotId);}
 @PostMapping("/findings/query") public Object findings(Authentication a,@RequestBody Map<String,Object> query){return queries.query(a,query,false);}
 @PostMapping("/source-records/query") public Object sources(Authentication a,@RequestBody Map<String,Object> query){return queries.query(a,query,true);}
 @GetMapping("/findings/{id}") public Object finding(Authentication a,@PathVariable String id,@RequestParam String snapshotId){return queries.trace(a,id,snapshotId,false,"");}
 @GetMapping("/source-records/{id}") public Object source(Authentication a,@PathVariable String id,@RequestParam String snapshotId,@RequestParam String kind){return queries.trace(a,id,snapshotId,true,kind);}
 @GetMapping("/templates/{kind}") public ResponseEntity<byte[]> template(Authentication a,@PathVariable String kind)throws Exception{ApiError.require(List.of("user","matrix","all").contains(kind),422,"TEMPLATE","模板类型无效");return download(codec.template(kind),"财经权限审查_"+kind+"_模板.xlsx");}
 @PostMapping("/products/{product}/import-jobs") public ResponseEntity<Object> upload(Authentication a,@PathVariable String product,@RequestPart MultipartFile file,@RequestParam(defaultValue="auto")String mode,@RequestParam(defaultValue="UNKNOWN")String rawCoverage,@RequestParam(defaultValue="FULL")String riskCoverage,@RequestParam String sourceAsOf,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception{return ResponseEntity.accepted().body(ingestion.upload(a,product,file,mode,rawCoverage,riskCoverage,sourceAsOf,key));}
 @GetMapping("/import-jobs")public Object imports(Authentication a){var scope=access.scope(a);if(scope.isEmpty())return List.of();return db.rows("SELECT j.id,j.product_id,j.draft_id,j.status,j.progress,j.created_at,a.original_name FROM import_job j JOIN file_asset a ON j.asset_id=a.id WHERE j.product_id IN (:ids) ORDER BY j.created_at DESC LIMIT 100",Map.of("ids",scope));}
 @GetMapping("/import-jobs/{id}")public Object importJob(Authentication a,@PathVariable String id){return ingestion.job(a,id);}
 @GetMapping("/import-jobs/{id}/errors")public ResponseEntity<byte[]> importErrors(Authentication a,@PathVariable String id){var j=ingestion.job(a,id);StringBuilder s=new StringBuilder("\uFEFF工作表,行号,问题\r\n");if(j.get("errors") instanceof Map<?,?> m&&m.get("items") instanceof List<?> rows)for(Object obj:rows){Map<?,?> r=(Map<?,?>)obj;for(String k:List.of("sheet","row","message")){String v=String.valueOf(r.get(k));if(v.matches("^[=+@-].*"))v="'"+v;s.append('"').append(v.replace("\"","\"\"")).append("\",");}s.setLength(s.length()-1);s.append("\r\n");}return download(s.toString().getBytes(StandardCharsets.UTF_8),"导入校验错误.csv");}
 @PostMapping("/products/{product}/drafts")public Object createDraft(Authentication a,@PathVariable String product,@RequestBody Map<String,Object> body){String kind=str(body,"kind");return pubs.createDraft(a,product,kind.equals("both")?List.of("user","matrix"):List.of(kind));}
 @GetMapping("/drafts")public Object drafts(Authentication a){List<String> scope=access.scope(a);if(scope.isEmpty())return List.of();for(String p:scope)access.product(a,p,"write");return db.rows("SELECT d.*,p.name AS product_name FROM review_draft d JOIN product p ON d.product_id=p.id WHERE d.product_id IN (:ids) AND d.state IN ('DRAFT','VALIDATED') ORDER BY d.created_at DESC LIMIT 100",Map.of("ids",scope));}
 @GetMapping("/drafts/{id}")public Object draft(Authentication a,@PathVariable String id){return pubs.preview(a,id);}
 @GetMapping("/drafts/{id}/record")public Object draftRecord(Authentication a,@PathVariable String id,@RequestParam String originalId,@RequestParam String kind,@RequestParam(defaultValue="false")boolean source){ApiError.require(List.of("user","matrix").contains(kind),422,"CHAIN","链路无效");return review.findDraftRecord(a,id,originalId,kind,source);}
 @PatchMapping("/drafts/{id}/source-records/{row}")public Object editSource(Authentication a,@PathVariable String id,@PathVariable String row,@RequestParam String kind,@RequestHeader(value="If-Match",required=false)String match,@RequestBody Map<String,Object> body){return review.editSource(a,id,row,kind,match,body);}
 @PatchMapping("/drafts/{id}/findings/{row}")public Object editFinding(Authentication a,@PathVariable String id,@PathVariable String row,@RequestHeader(value="If-Match",required=false)String match,@RequestBody Map<String,Object> body){return review.editFinding(a,id,row,match,body);}
 @PostMapping("/drafts/{id}/publish")public Object publish(Authentication a,@PathVariable String id,@RequestHeader(value="If-Match",required=false)String match,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body){return pubs.publish(a,id,match,key,body);}
 @DeleteMapping("/drafts/{id}")public Object discard(Authentication a,@PathVariable String id,@RequestHeader(value="If-Match",required=false)String match){pubs.discard(a,id,match);return map("discarded",true);}
 @GetMapping("/products/{id}/releases")public Object history(Authentication a,@PathVariable String id){return map("current",pubs.publication(id),"releases",pubs.history(a,id));}
 @PostMapping("/products/{id}/rollback")public Object rollback(Authentication a,@PathVariable String id,@RequestHeader(value="If-Match",required=false)String match,@RequestBody Map<String,Object> body){return pubs.rollback(a,id,str(body,"releaseId"),match,str(body,"reason"));}
 @GetMapping("/activity")public Object activity(Authentication a){List<String> scope=access.scope(a);if(scope.isEmpty())return List.of();return db.rows("SELECT e.id,e.product_id,e.actor,e.action,e.reason,e.created_at,p.name AS product_name FROM audit_event e JOIN product p ON e.product_id=p.id WHERE e.product_id IN (:ids) ORDER BY e.created_at DESC LIMIT 100",Map.of("ids",scope));}
 @PostMapping("/export-jobs")public ResponseEntity<Object> export(Authentication a,@RequestBody Map<String,Object> query,@RequestHeader(value="Idempotency-Key",required=false)String key){return ResponseEntity.accepted().body(exports.create(a,query,key));}
 @GetMapping("/export-jobs/{id}")public Object exportJob(Authentication a,@PathVariable String id){return exports.get(a,id);}
 @GetMapping("/export-jobs/{id}/file")public ResponseEntity<byte[]> exportFile(Authentication a,@PathVariable String id)throws Exception{var j=exports.get(a,id);ApiError.require(str(j,"status").equals("SUCCEEDED"),409,"EXPORT_PENDING","导出尚未完成");return download(Files.readAllBytes(files.path(str(j,"storageName"))),"财经权限审查_"+str(j,"chain")+".xlsx");}
 @GetMapping("/files/{id}")public ResponseEntity<byte[]> file(Authentication a,@PathVariable String id)throws Exception{var file=db.one("SELECT * FROM file_asset WHERE id=:id",Map.of("id",id));access.product(a,str(file,"productId"),"export");db.event(str(file,"productId"),access.actor(a),"DOWNLOAD_SOURCE",id,null,null,"下载来源文件");return download(Files.readAllBytes(files.path(str(file,"storageName"))),str(file,"originalName"));}
 static ResponseEntity<byte[]> download(byte[] bytes,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes);}
}
