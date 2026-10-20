package cn.finance.review;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
@Configuration
public class SecurityConfig {
 @Bean UserDetailsService users(Environment env,@Value("${audit.demo:false}") boolean demo){
  List<UserDetails> list=new ArrayList<>();var encoder=new BCryptPasswordEncoder();
  for(String name:List.of("aa","leader","product","quality","platform")){
   String password=env.getProperty("audit.accounts."+name,"");
   if(password.isBlank()&&demo)password=name+"-demo-2026";
   if(!password.isBlank())list.add(User.withUsername(name).password("{bcrypt}"+encoder.encode(password)).roles(name.toUpperCase(Locale.ROOT)).build());
  }
  if(list.isEmpty())throw new IllegalStateException("正式运行必须配置 AA_PASSWORD 等账号密钥，或接入企业身份认证。");
  return new org.springframework.security.provisioning.InMemoryUserDetailsManager(list);
 }
 @Bean @org.springframework.core.annotation.Order(2) SecurityFilterChain security(HttpSecurity http)throws Exception {
  return http.authorizeHttpRequests(a->a.requestMatchers("/api/**").authenticated().anyRequest().permitAll())
   .httpBasic(b->b.authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"请登录审查工作台\"}");}))
   .csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).sessionAuthenticationStrategy(new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy()))
   .sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
   .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'")))
   .build();
 }
}
