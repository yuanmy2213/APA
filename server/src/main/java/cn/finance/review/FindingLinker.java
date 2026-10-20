package cn.finance.review;

import java.util.*;
import static cn.finance.review.Db.*;

/** Business IDs resolve within one product/version; names never serve as fallback keys. */
public final class FindingLinker {
 public record Match(String status, String sourceId, String method, String reason, int candidates, Map<String,Object> detail) {}
 private final String field,method,notFound;
 private final Map<String,List<Map<String,Object>>> sourcesById=new HashMap<>();
 public FindingLinker(List<Map<String,Object>> sources,String chain){
  boolean matrix=chain.equals("matrix");field=matrix?"permissionCombinationId":"authorizationId";method=matrix?"COMBINATION_ID":"AUTHORIZATION_ID";notFound=matrix?"COMBINATION_ID_NOT_FOUND":"AUTHORIZATION_ID_NOT_FOUND";
  for(var source:sources){String id=str(source,field);if(!id.isBlank())sourcesById.computeIfAbsent(id,k->new ArrayList<>()).add(source);}
 }
 static String text(String value){return value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 private static boolean compatible(Map<String,?> a,Map<String,?> b,String key){String x=key.endsWith("Id")?str(a,key).trim():text(str(a,key)),y=key.endsWith("Id")?str(b,key).trim():text(str(b,key));return x.isBlank()||y.isBlank()||x.equals(y);}
 private static boolean scopeMatches(Map<String,?> finding,Map<String,?> source){return compatible(finding,source,"sourceSystem")&&compatible(finding,source,"tenantId")&&(!str(finding,"appId").isBlank()&&!str(source,"appId").isBlank()?compatible(finding,source,"appId"):compatible(finding,source,"application"));}
 public Match match(Map<String,?> finding){
  String businessId=str(finding,field);var pool=sourcesById.getOrDefault(businessId,List.of());var candidates=pool.stream().filter(s->scopeMatches(finding,s)).toList();int size=candidates.size();
  String reason=businessId.isBlank()?"BUSINESS_ID_MISSING":pool.isEmpty()?notFound:size==0?"SCOPE_CONFLICT":size>1?"MULTIPLE_CANDIDATES":method;
  var missing=new ArrayList<String>();for(String key:List.of("sourceSystem","appId","application","tenantId"))if(str(finding,key).isBlank())missing.add(key);
  return new Match(size==0?"UNLINKED":size>1?"AMBIGUOUS":"LINKED",size==1?str(candidates.get(0),"id"):null,method,reason,size,map("businessIdField",field,"businessId",businessId,"missingFields",missing,"candidateSourceIds",candidates.stream().limit(5).map(s->s.get("id")).toList(),"candidateCount",size));
 }
}
