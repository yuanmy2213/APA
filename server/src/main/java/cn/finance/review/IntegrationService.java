package cn.finance.review;

import java.io.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static cn.finance.review.Db.*;
import static cn.finance.review.ApiError.require;

@Service
public class IntegrationService {
 final Db db;final Access access;final IngestionService ingestion;final PublicationService pubs;final QueryService queries;
 public IntegrationService(Db db,Access access,IngestionService ingestion,PublicationService pubs,QueryService queries){this.db=db;this.access=access;this.ingestion=ingestion;this.pubs=pubs;this.queries=queries;}

 public Map<String,Object> uploadJson(Authentication a,String product,Map<String,Object> body,String key)throws Exception{
  access.product(a,product,"write");require(body.get("datasets") instanceof List<?> list&&!list.isEmpty()&&list.size()<=4,422,"JSON_DATASETS","datasets应包含1至4类核心数据");
  require("1".equals(str(body,"schemaVersion")),422,"JSON_SCHEMA","schemaVersion必须为1");
  byte[] data=db.encode(canonical(map("schemaVersion","1","datasets",body.get("datasets")))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  require(data.length<=50*1024*1024,413,"FILE_SIZE","请求超过50MB");
  return ingestion.upload(a,product,new JsonUpload(data),str(body,"mode").isBlank()?"auto":str(body,"mode"),str(body,"rawCoverage").isBlank()?"UNKNOWN":str(body,"rawCoverage"),str(body,"riskCoverage").isBlank()?"FULL":str(body,"riskCoverage"),str(body,"sourceAsOf"),key);
 }
 static Object canonical(Object value){if(value instanceof Map<?,?> map){var result=new TreeMap<String,Object>();map.forEach((k,v)->result.put(String.valueOf(k),canonical(v)));return result;}if(value instanceof List<?> list)return list.stream().map(IntegrationService::canonical).toList();return value;}
 record JsonUpload(byte[] data) implements MultipartFile {
  public String getName(){return "file";}public String getOriginalFilename(){return "integration.json";}public String getContentType(){return "application/json";}public boolean isEmpty(){return data.length==0;}public long getSize(){return data.length;}public byte[] getBytes(){return data;}public InputStream getInputStream(){return new ByteArrayInputStream(data);}public void transferTo(File file)throws IOException{java.nio.file.Files.write(file.toPath(),data);}
 }

 /** Resolve business identity only within the authorized draft's versioned dataset. */
 public Map<String,Object> resolve(Authentication a,String draftId,Map<String,Object> body){
  var draft=pubs.draft(a,draftId,false);pubs.editable(draft);String chain=str(body,"kind");require(List.of("user","matrix").contains(chain),422,"CHAIN","kind应为user或matrix");
  String dataset=str(draft,chain+"DatasetId"),businessId=str(body,"businessId");require(!dataset.isBlank(),422,"CHAIN","草稿不包含该链路");require(!businessId.isBlank()&&businessId.length()<=200,422,"BUSINESS_ID","请提供有效业务标识");
  require(body.get("businessId") instanceof String,422,"BUSINESS_ID","业务标识必须为字符串，以保留前导0");require(!body.containsKey("source")||body.get("source") instanceof Boolean,422,"SOURCE_FLAG","source必须为布尔值");
  boolean source=!Boolean.FALSE.equals(body.get("source"));String alias=source?"s":"d",field=chain.equals("user")?"authorization_id":"permission_combination_id";
  var args=map("dataset",dataset,"businessId",businessId);String from=source?" FROM "+Fields.sourceTable(chain)+" s":" FROM risk_finding f JOIN "+Fields.detailTable(chain)+" d ON d.finding_id=f.id";
  StringBuilder where=new StringBuilder(" WHERE "+(source?"s":"f")+".dataset_id=:dataset AND "+alias+"."+field+"=:businessId");
  if(!source){String rule=str(body,"ruleCode");require(rule.matches(chain.equals("user")?"[1-7]":"8[a-f]"),422,"RULE_CODE","定位风险须指定检查项ruleCode");where.append(" AND f.rule_code=:rule");args.put("rule",rule);}
  for(String scope:List.of("appId","application","tenantId","sourceSystem"))if(!str(body,scope).isBlank()){where.append(" AND ").append(alias).append('.').append(snake(scope)).append("=:").append(scope);args.put(scope,str(body,scope));}
  var rows=db.rows("SELECT "+(source?"s.*":"f.*,d.*")+from+where+" LIMIT 2",args);
  require(!rows.isEmpty(),404,"NOT_FOUND","该业务标识在当前草稿中不存在");require(rows.size()==1,409,"AMBIGUOUS_BUSINESS_ID","存在多条匹配记录，请补充应用、租户或来源范围");var row=rows.get(0);
  return source?map("draft",draft,"source",row):map("draft",draft,"finding",row,"source",queries.source(chain,str(row,"currentSourceId")));
 }
}
