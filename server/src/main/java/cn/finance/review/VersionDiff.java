package cn.finance.review;

import java.util.*;
import org.springframework.stereotype.Component;
import static cn.finance.review.Db.*;

/** Multiset comparison of effective findings: counts alone cannot reveal replacements. */
@Component
public class VersionDiff {
 final Db db;
 public VersionDiff(Db db){this.db=db;}
 private List<Map<String,Object>> findings(String dataset,String chain){if(dataset==null||dataset.isBlank())return List.of();return db.rows("SELECT f.*,d.* FROM risk_finding f JOIN "+Fields.detailTable(chain)+" d ON f.id=d.finding_id WHERE f.dataset_id=:id AND f.workflow<>'已确认误报' ORDER BY f.id",map("id",dataset));}
 private String key(Map<String,Object> f,String chain){return str(f,"ruleCode")+":"+Fields.key(f,chain);}
 private String signature(Map<String,Object> f,String chain){List<String> keys=new ArrayList<>(Fields.COMMON);keys.remove("checkName");keys.addAll(Fields.detailColumns(chain));return db.encode(Fields.select(f,keys));}
 private static String rule(Map<String,Object> f){return str(f,"chain").equals("matrix")?"8":str(f,"ruleCode");}
 private static Map<String,Object> bucket(String id,String name){return map("id",id,"name",name,"oldCount",0,"newCount",0,"added",0,"removed",0,"maintained",0,"changed",0);}
 private static void inc(Map<String,Object> b,String field){b.put(field,num(b,field)+1);}
 public Map<String,Object> compare(String product,String oldUser,String oldMatrix,String newUser,String newMatrix){
  Map<String,Map<String,Object>> byRule=new LinkedHashMap<>(),byLevel=new LinkedHashMap<>();for(int i=1;i<=8;i++)byRule.put(String.valueOf(i),bucket(String.valueOf(i),Fields.RULE_NAMES[i-1]));for(String level:List.of("高","中","低"))byLevel.put(level,bucket(level,level+"风险"));var total=bucket(product,product);
  for(String chain:List.of("user","matrix")){
   var before=findings(chain.equals("user")?oldUser:oldMatrix,chain);var after=findings(chain.equals("user")?newUser:newMatrix,chain);
   Map<String,List<Map<String,Object>>> oldRows=new LinkedHashMap<>(),newRows=new LinkedHashMap<>();for(var r:before){oldRows.computeIfAbsent(key(r,chain),k->new ArrayList<>()).add(r);count(total,byRule,byLevel,r,"oldCount");}for(var r:after){newRows.computeIfAbsent(key(r,chain),k->new ArrayList<>()).add(r);count(total,byRule,byLevel,r,"newCount");}
   Set<String> keys=new LinkedHashSet<>(oldRows.keySet());keys.addAll(newRows.keySet());
   for(String key:keys){var olds=new ArrayList<>(oldRows.getOrDefault(key,List.of()));var news=new ArrayList<>(newRows.getOrDefault(key,List.of()));
    // Pair exact values first, then pair changed values under the same business identity.
    for(var it=olds.iterator();it.hasNext();){var old=it.next();var same=news.stream().filter(n->signature(old,chain).equals(signature(n,chain))).findFirst();if(same.isPresent()){count(total,byRule,byLevel,same.get(),"maintained");news.remove(same.get());it.remove();}}
    int paired=Math.min(olds.size(),news.size());for(int i=0;i<paired;i++){var old=olds.get(i);var next=news.get(i);count(total,byRule,byLevel,next,"maintained");count(total,byRule,byLevel,next,"changed");if(!str(old,"risk").equals(str(next,"risk"))){byLevel.get(str(next,"risk")).put("changed",num(byLevel.get(str(next,"risk")),"changed")-1);inc(byLevel.get(str(old,"risk")),"removed");inc(byLevel.get(str(next,"risk")),"added");}}
    for(int i=paired;i<olds.size();i++)count(total,byRule,byLevel,olds.get(i),"removed");for(int i=paired;i<news.size();i++)count(total,byRule,byLevel,news.get(i),"added");
   }
  }
  // Level maintained is the intersection of old/new membership; level changes are migrations.
  for(var level:byLevel.values())level.put("maintained",num(level,"newCount")-num(level,"added"));
  return map("productId",product,"summary",total,"byRule",new ArrayList<>(byRule.values()),"byLevel",new ArrayList<>(byLevel.values()),"definition","仅比较未确认误报的风险记录。移出指不再计入本轮清单，不代表生产权限已整改。变更为维持记录的子集。");
 }
 private static void count(Map<String,Object> total,Map<String,Map<String,Object>> rules,Map<String,Map<String,Object>> levels,Map<String,Object> row,String field){inc(total,field);var rule=rules.get(rule(row));if(rule!=null)inc(rule,field);var level=levels.get(str(row,"risk"));if(level!=null)inc(level,field);}
}
