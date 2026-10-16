package cn.finance.review;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class Catalog implements ApplicationRunner {
 final Db db; public Catalog(Db db){this.db=db;}
 public void run(ApplicationArguments args){db.transaction(()->{for(int i=0;i<Fields.PRODUCT_NAMES.length;i++){String id=String.format("P%02d",i+1);if(db.count("SELECT COUNT(*) FROM product WHERE id=:id",Map.of("id",id))==0){db.insert("product",Db.map("id",id,"name",Fields.PRODUCT_NAMES[i],"icon",Fields.ICONS[i],"application","未提供"));db.insert("product_publication",Db.map("productId",id,"releaseId",null,"revision",0));}}return null;});}
}
