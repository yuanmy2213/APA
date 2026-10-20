package cn.finance.review;

import java.sql.*;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MigrationCompatibilityTest {
 @Test void v2PointAndMatrixRowsGainStableBusinessIdsWithoutChangingEvidence()throws Exception {
  Path folder=Files.createTempDirectory("review-v2-upgrade-");String url="jdbc:h2:file:"+folder.resolve("review")+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
  try{
   Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
   try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){
    s.execute("INSERT INTO product VALUES ('P06','财经法务产品','scale','测试应用')");
    s.execute("INSERT INTO review_dataset(id,product_id,chain,state,raw_coverage,risk_coverage,source_as_of,raw_as_of,risk_as_of,created_at) VALUES('matrix-dataset','P06','matrix','PUBLISHED','FULL','FULL','','','','2026-10-19T00:00:00Z')");
    s.execute("INSERT INTO source_point(id,dataset_id,record_key,role_name,set_name,point_id,record_version) VALUES('point','matrix-dataset','old-storage-key','角色','权限集','000007',1)");
    s.execute("INSERT INTO risk_finding(id,dataset_id,chain,rule_code,risk,workflow,violation,current_source_id,audited_source_id,link_status,record_version) VALUES('risk','matrix-dataset','matrix','8a','高','待核实','风险原因与判定依据不变','point','point','LINKED',1)");
    s.execute("INSERT INTO matrix_finding(finding_id,role_name,set_name,point_id) VALUES('risk','角色','权限集','000007')");
   }
   assertEquals(1,Flyway.configure().dataSource(url,"sa","").load().migrate().migrationsExecuted);
   try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){
    try(ResultSet rs=s.executeQuery("SELECT p.permission_combination_id,m.permission_combination_id,p.point_id,p.record_key,f.violation,f.current_source_id FROM source_point p JOIN matrix_finding m ON m.permission_combination_id=p.permission_combination_id JOIN risk_finding f ON f.id=m.finding_id")){
     assertTrue(rs.next());assertEquals("LEGACY-old-storage-key",rs.getString(1));assertEquals(rs.getString(1),rs.getString(2));assertEquals("000007",rs.getString(3));assertEquals("old-storage-key",rs.getString(4));assertEquals("风险原因与判定依据不变",rs.getString(5));assertEquals("point",rs.getString(6));
    }
    assertThrows(SQLException.class,()->s.execute("INSERT INTO source_point(id,dataset_id,record_key,permission_combination_id,record_version) VALUES('duplicate','matrix-dataset','different-key','LEGACY-old-storage-key',1)"));
   }
  }finally{try(var paths=Files.walk(folder)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 @Test void v1FileDatabaseUpgradesWithoutRewritingRiskOrSource()throws Exception {
  Path folder=Files.createTempDirectory("review-v1-upgrade-");String url="jdbc:h2:file:"+folder.resolve("review")+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
  try{
   Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
   try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){
    s.execute("INSERT INTO product VALUES ('P06','财经法务产品','scale','测试应用')");
    s.execute("INSERT INTO review_dataset(id,product_id,chain,state,raw_coverage,risk_coverage,source_as_of,raw_as_of,risk_as_of,created_at) VALUES('dataset','P06','user','PUBLISHED','FULL','FULL','','','','2026-10-17T00:00:00Z')");
    s.execute("INSERT INTO source_auth(id,dataset_id,record_key,user_key,employee_id,authorization_id,record_version) VALUES('source','dataset','legacy-storage-key','employee-key','L00566785','00001',1)");
    s.execute("INSERT INTO risk_finding(id,dataset_id,chain,rule_code,risk,workflow,violation,current_source_id,link_status,record_version) VALUES('finding','dataset','user','1','高','待核实','原始风险原因包含业务审批，值保持不变','source','LINKED',1)");
   }
   var migrated=Flyway.configure().dataSource(url,"sa","").load().migrate();assertEquals(2,migrated.migrationsExecuted);
   try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){
    try(ResultSet r=s.executeQuery("SELECT violation,current_source_id,link_candidates FROM risk_finding")){assertTrue(r.next());assertEquals("原始风险原因包含业务审批，值保持不变",r.getString(1));assertEquals("source",r.getString(2));assertEquals(0,r.getInt(3));}
    try(ResultSet r=s.executeQuery("SELECT authorization_id,record_key FROM source_auth")){assertTrue(r.next());assertEquals("00001",r.getString(1));assertEquals("legacy-storage-key",r.getString(2));}
   }
  }finally{try(var paths=Files.walk(folder)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
