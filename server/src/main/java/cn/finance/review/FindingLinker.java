package cn.finance.review;

import java.util.*;
import static cn.finance.review.Db.*;

/** Candidate association is separate from the import uniqueness/storage key. */
public final class FindingLinker {
 public record Match(String status, String sourceId, String method, String reason, int candidates, Map<String,Object> detail) {}
 private final String chain;
 private final Map<String,List<Map<String,Object>>> exact=new HashMap<>(), identities=new HashMap<>(), grants=new HashMap<>(), combinations=new HashMap<>();
 public FindingLinker(List<Map<String,Object>> sources,String chain){
  this.chain=chain;
  for(var s:sources){
   String key=Fields.key(s,chain);add(exact,key,s);add(identities,identity(s),s);
   if(chain.equals("user")&&!str(s,"authorizationId").isBlank())add(grants,str(s,"authorizationId"),s);
   if(chain.equals("matrix")){
    if(!str(s,"permissionCombinationId").isBlank())add(combinations,str(s,"permissionCombinationId"),s);
    String legacy=Fields.legacyKey(s,chain);if(!legacy.equals(key))add(exact,legacy,s);
   }
  }
 }
 private static void add(Map<String,List<Map<String,Object>>> index,String key,Map<String,Object> row){index.computeIfAbsent(key,k->new ArrayList<>()).add(row);}
 private String identity(Map<String,?> r){String value=str(r,chain.equals("user")?"employeeId":"pointId").trim();return chain.equals("user")&&value.matches("[A-Za-z]*[0-9]+")?value.toLowerCase(Locale.ROOT):value;}
 static String text(String value){return value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 private static boolean compatible(Map<String,?> a,Map<String,?> b,String key){String x=key.endsWith("Id")?str(a,key).trim():text(str(a,key)),y=key.endsWith("Id")?str(b,key).trim():text(str(b,key));return x.isBlank()||y.isBlank()||x.equals(y);}
 private static boolean role(Map<String,?> a,Map<String,?> b,String id,String name){return !str(a,id).isBlank()&&!str(b,id).isBlank()?compatible(a,b,id):compatible(a,b,name);}
 private boolean objectMatches(Map<String,?> f,Map<String,?> s){
  if(chain.equals("user"))return (identity(f).isBlank()||identity(f).equals(identity(s)))&&compatible(f,s,"authorizationId")&&role(f,s,"roleId","permissionName");
  return role(f,s,"roleId","roleName")&&role(f,s,"setId","setName");
 }
 private boolean scopeMatches(Map<String,?> f,Map<String,?> s){return compatible(f,s,"sourceSystem")&&compatible(f,s,"tenantId")&&(!str(f,"appId").isBlank()&&!str(s,"appId").isBlank()?compatible(f,s,"appId"):compatible(f,s,"application"));}
 public Match match(Map<String,?> finding){
  if(chain.equals("matrix")&&!str(finding,"permissionCombinationId").isBlank()){
   var pool=combinations.getOrDefault(str(finding,"permissionCombinationId"),List.of());
   var candidates=pool.stream().filter(s->scopeMatches(finding,s)).toList();
   return result(candidates,false,"COMBINATION_ID",pool.isEmpty()?"COMBINATION_ID_NOT_FOUND":candidates.isEmpty()?"SCOPE_CONFLICT":"COMBINATION_ID",finding);
  }
  var precise=exact.getOrDefault(Fields.key(finding,chain),List.of());
  if(!precise.isEmpty())return result(precise,false,"EXACT_KEY","EXACT_KEY",finding);
  List<Map<String,Object>> pool;
  String method;
  if(chain.equals("user")&&!str(finding,"authorizationId").isBlank()){
   pool=grants.getOrDefault(str(finding,"authorizationId"),List.of());method="AUTH_ID_PARTIAL_SCOPE";
   if(pool.isEmpty())return result(List.of(),true,method,"AUTHORIZATION_ID_NOT_FOUND",finding);
  }else{pool=identities.getOrDefault(identity(finding),List.of());method=chain.equals("user")?"USER_ROLE_FALLBACK":"POINT_BINDING_FALLBACK";}
  var objects=pool.stream().filter(s->objectMatches(finding,s)).toList();
  var compatible=objects.stream().filter(s->scopeMatches(finding,s)).toList();
  String reason=compatible.isEmpty()?(pool.isEmpty()?"SOURCE_NOT_FOUND":objects.isEmpty()?"OBJECT_MISMATCH":"SCOPE_CONFLICT"):"PARTIAL_SCOPE_UNIQUE";
  return result(compatible,true,method,reason,finding);
 }
 private Match result(List<Map<String,Object>> matches,boolean weak,String method,String reason,Map<String,?> f){
  int size=matches.size();String status=size==0?"UNLINKED":size>1?"AMBIGUOUS":weak?"LINKED_WEAK":"LINKED";
  var missing=new ArrayList<String>();for(String k:List.of("sourceSystem","appId","application","tenantId"))if(str(f,k).isBlank())missing.add(k);
  String field=chain.equals("matrix")?"permissionCombinationId":"authorizationId";
  return new Match(status,size==1?str(matches.get(0),"id"):null,method,size>1?"MULTIPLE_CANDIDATES":reason,size,map("businessIdField",field,"businessId",str(f,field),"missingFields",missing,"candidateSourceIds",matches.stream().limit(5).map(s->s.get("id")).toList(),"candidateCount",size));
 }
}
