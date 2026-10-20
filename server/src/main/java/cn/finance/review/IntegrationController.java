package cn.finance.review;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.finance.review.Db.*;

/** Versioned API for authorized automation. It shares the UI's domain services and audit trail. */
@RestController
@RequestMapping("/api/integration/v1")
public class IntegrationController {
 final IntegrationService integration;final IngestionService ingestion;final PublicationService pubs;final ReviewService review;final QueryService queries;final WorkbookCodec codec;
 public IntegrationController(IntegrationService integration,IngestionService ingestion,PublicationService pubs,ReviewService review,QueryService queries,WorkbookCodec codec){this.integration=integration;this.ingestion=ingestion;this.pubs=pubs;this.review=review;this.queries=queries;this.codec=codec;}
 @PostMapping("/products/{product}/imports")
 public ResponseEntity<?> json(Authentication a,@PathVariable String product,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body)throws Exception{return ResponseEntity.accepted().body(integration.uploadJson(a,product,body,key));}
 @PostMapping(value="/products/{product}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<?> file(Authentication a,@PathVariable String product,@RequestPart MultipartFile file,@RequestParam(defaultValue="auto")String mode,@RequestParam(defaultValue="UNKNOWN")String rawCoverage,@RequestParam(defaultValue="FULL")String riskCoverage,@RequestParam String sourceAsOf,@RequestHeader("Idempotency-Key")String key)throws Exception{return ResponseEntity.accepted().body(ingestion.upload(a,product,file,mode,rawCoverage,riskCoverage,sourceAsOf,key));}
 @GetMapping("/import-jobs/{id}") public Object job(Authentication a,@PathVariable String id){return ingestion.job(a,id);}
 @GetMapping("/import-jobs/{id}/errors/page") public Object errors(Authentication a,@PathVariable String id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="100")int pageSize){return ingestion.errors(a,id,page,pageSize);}
 @GetMapping("/templates/{kind}") public ResponseEntity<byte[]> template(@PathVariable String kind)throws Exception{ApiError.require(List.of("user","matrix","all").contains(kind),422,"TEMPLATE","模板类型无效");return ApiController.download(codec.template(kind),"财经权限审查_"+kind+"_模板.xlsx");}
 @PostMapping("/snapshots") public Object snapshot(Authentication a){return queries.snapshot(a);}
 @PostMapping("/source-records/query") public Object sources(Authentication a,@RequestBody Map<String,Object> q){return queries.query(a,q,true);}
 @PostMapping("/findings/query") public Object findings(Authentication a,@RequestBody Map<String,Object> q){return queries.query(a,q,false);}
 @PostMapping("/products/{product}/drafts") public Object draft(Authentication a,@PathVariable String product,@RequestBody Map<String,Object> body){String kind=str(body,"kind");return pubs.createDraft(a,product,kind.equals("both")?List.of("user","matrix"):List.of(kind));}
 @GetMapping("/drafts/{id}") public Object preview(Authentication a,@PathVariable String id){return pubs.preview(a,id);}
 @GetMapping("/drafts/{id}/record") public Object original(Authentication a,@PathVariable String id,@RequestParam String originalId,@RequestParam String kind,@RequestParam(defaultValue="false")boolean source){ApiError.require(List.of("user","matrix").contains(kind),422,"CHAIN","链路无效");return review.findDraftRecord(a,id,originalId,kind,source);}
 @PostMapping("/drafts/{id}/records/resolve") public Object resolve(Authentication a,@PathVariable String id,@RequestBody Map<String,Object> body){return integration.resolve(a,id,body);}
 @PatchMapping("/drafts/{id}/source-records/{row}") public Object source(Authentication a,@PathVariable String id,@PathVariable String row,@RequestParam String kind,@RequestHeader(value="If-Match",required=false)String match,@RequestBody Map<String,Object> body){return review.editSource(a,id,row,kind,match,body);}
 @PatchMapping("/drafts/{id}/findings/{row}") public Object finding(Authentication a,@PathVariable String id,@PathVariable String row,@RequestHeader(value="If-Match",required=false)String match,@RequestBody Map<String,Object> body){return review.editFinding(a,id,row,match,body);}
 @PostMapping("/drafts/{id}/publish") public Object publish(Authentication a,@PathVariable String id,@RequestHeader(value="If-Match",required=false)String match,@RequestHeader("Idempotency-Key")String key,@RequestBody Map<String,Object> body){return pubs.publish(a,id,match,key,body);}
}
