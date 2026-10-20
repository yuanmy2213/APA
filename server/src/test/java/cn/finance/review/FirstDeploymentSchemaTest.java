package cn.finance.review;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstDeploymentSchemaTest {
 @Test void cleanSchemaContainsBusinessIdsAndRequiredConstraints()throws Exception {
  String url="jdbc:h2:mem:firstdeploy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  var flyway=Flyway.configure().dataSource(url,"sa","").load();assertEquals(1,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){
   s.execute("INSERT INTO product VALUES('P06','财经法务产品','scale','测试')");
   s.execute("INSERT INTO review_dataset(id,product_id,chain,state,raw_coverage,risk_coverage,source_as_of,raw_as_of,risk_as_of,created_at) VALUES('d','P06','matrix','DRAFT','FULL','FULL','','','','now')");
   s.execute("INSERT INTO source_point(id,dataset_id,record_key,permission_combination_id,record_version) VALUES('p1','d','k1','000-C1',1)");
   assertThrows(SQLException.class,()->s.execute("INSERT INTO source_point(id,dataset_id,record_key,permission_combination_id,record_version) VALUES('p2','d','k2','000-C1',1)"));
   assertThrows(SQLException.class,()->s.execute("INSERT INTO source_point(id,dataset_id,record_key,record_version) VALUES('p3','d','k3',1)"));
   try(ResultSet rs=s.executeQuery("SELECT permission_combination_id FROM source_point")){assertTrue(rs.next());assertEquals("000-C1",rs.getString(1));}
   try(ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL")){assertTrue(rs.next());assertEquals(1,rs.getInt(1));}
  }
 }
}
