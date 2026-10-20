package cn.finance.review;
import java.util.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class Db {
 final NamedParameterJdbcTemplate jdbc; final ObjectMapper json; final TransactionTemplate tx;
 public Db(NamedParameterJdbcTemplate jdbc,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager tm){this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(tm);}
 public static String id(){return UUID.randomUUID().toString();}
 public static String now(){return java.time.Instant.now().toString();}
 public static String snake(String s){return s.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
 public static String camel(String s){StringBuilder out=new StringBuilder();boolean up=false;for(char c:s.toLowerCase(Locale.ROOT).toCharArray()){if(c=='_')up=true;else{out.append(up?Character.toUpperCase(c):c);up=false;}}return out.toString();}
 public List<Map<String,Object>> rows(String sql,Map<String,?> p){return jdbc.query(sql,p,(rs,n)->{Map<String,Object> r=new LinkedHashMap<>();var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++)r.put(camel(md.getColumnLabel(i)),rs.getObject(i));return r;});}
 public Map<String,Object> one(String sql,Map<String,?> p){var a=rows(sql,p);if(a.isEmpty())throw new ApiError(404,"NOT_FOUND","记录不存在或无访问权限");return a.get(0);}
 public Map<String,Object> optional(String sql,Map<String,?> p){var a=rows(sql,p);return a.isEmpty()?null:a.get(0);}
 public int update(String sql,Map<String,?> p){return jdbc.update(sql,p);}
 public long count(String sql,Map<String,?> p){Long r=jdbc.queryForObject(sql,p,Long.class);return r==null?0:r;}
 public void insert(String table,Map<String,?> values){String cols=String.join(",",values.keySet().stream().map(Db::snake).toList());String params=String.join(",",values.keySet().stream().map(k->":"+k).toList());update("INSERT INTO "+table+" ("+cols+") VALUES ("+params+")",values);}
 public void insertBatch(String table,List<Map<String,Object>> rows){if(rows.isEmpty())return;var keys=rows.get(0).keySet();String cols=String.join(",",keys.stream().map(Db::snake).toList());String values=String.join(",",keys.stream().map(k->":"+k).toList());jdbc.batchUpdate("INSERT INTO "+table+" ("+cols+") VALUES ("+values+")",org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils.createBatch(rows.toArray()));}
 public void updateBatch(String sql,List<Map<String,Object>> rows){if(!rows.isEmpty())jdbc.batchUpdate(sql,org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils.createBatch(rows.toArray()));}
 public <T>T transaction(Supplier<T> work){return tx.execute(s->work.get());}
 public String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> decode(String x){try{return x==null?new LinkedHashMap<>():json.readValue(x,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 public static String str(Map<String,?> r,String k){Object v=r.get(k);return v==null?"":String.valueOf(v);}
 public static int num(Map<String,?> r,String k){Object v=r.get(k);return v==null?0:((Number)v).intValue();}
 public void event(String product,String actor,String action,String target,Object before,Object after,String reason){insert("audit_event",map("id",id(),"productId",product,"actor",actor,"action",action,"targetId",target,"beforeValue",before==null?null:encode(before),"afterValue",after==null?null:encode(after),"reason",reason,"createdAt",now()));}
 public static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
}
