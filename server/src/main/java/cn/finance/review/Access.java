package cn.finance.review;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import static cn.finance.review.ApiError.require;
@Component
public class Access {
 final Db db; final Set<String> productScope,integrationScope;
 public Access(Db db,@Value("${audit.product-scope:P06}")String scope,@Value("${audit.integration.products:}")String integrations){this.db=db;this.productScope=Arrays.stream(scope.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toSet());this.integrationScope=Arrays.stream(integrations.split(",")).map(String::trim).filter(x->!x.isEmpty()).collect(Collectors.toSet());}
 public String actor(Authentication a){require(a!=null&&a.isAuthenticated(),401,"UNAUTHENTICATED","请先登录");return a.getName();}
 public Set<String> roles(Authentication a){return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","")).collect(Collectors.toSet());}
 public List<String> scope(Authentication a){var roles=roles(a);if(roles.contains("PLATFORM")||roles.stream().noneMatch(x->List.of("AA","LEADER","QUALITY","PRODUCT","INTEGRATION").contains(x)))return List.of();var all=db.rows("SELECT id FROM product ORDER BY id",Map.of()).stream().map(r->Db.str(r,"id")).toList();if(roles.contains("INTEGRATION"))return all.stream().filter(p->integrationScope.contains("*")||integrationScope.contains(p)).toList();if(roles.contains("PRODUCT"))return all.stream().filter(productScope::contains).toList();return all;}
 public void product(Authentication a,String id,String operation){actor(a);require(scope(a).contains(id),404,"NOT_FOUND","记录不存在或无访问权限");var r=roles(a);boolean ok=switch(operation){case "write"->r.contains("INTEGRATION")||r.contains("AA")||r.contains("QUALITY")||r.contains("PRODUCT");case "publish"->r.contains("INTEGRATION_PUBLISH")||r.contains("AA")||r.contains("QUALITY");case "export"->r.contains("INTEGRATION")||r.contains("AA")||r.contains("QUALITY")||r.contains("LEADER")||r.contains("PRODUCT");default->true;};require(ok,403,"FORBIDDEN","当前身份没有此操作权限");}
 public String scopeHash(Authentication a){return Fields.hash(String.join(",",scope(a)));}
 public Map<String,Object> me(Authentication a,boolean demo){return Db.map("name",actor(a),"roles",roles(a),"products",scope(a),"demo",demo,"canWrite",roles(a).stream().anyMatch(x->List.of("AA","QUALITY","PRODUCT","INTEGRATION").contains(x)),"canPublish",roles(a).stream().anyMatch(x->List.of("AA","QUALITY","INTEGRATION_PUBLISH").contains(x)));}
}
