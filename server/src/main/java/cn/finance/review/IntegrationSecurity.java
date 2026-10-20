package cn.finance.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Dedicated server-to-server surface. Browser Basic/CSRF behavior is unchanged. */
@Configuration
public class IntegrationSecurity {
 @Bean @Order(1)
 SecurityFilterChain integrationApiChain(HttpSecurity http,
   @Value("${audit.integration.token:}") String token,
   @Value("${audit.integration.can-publish:false}") boolean canPublish)throws Exception {
  if(!token.isBlank()&&token.length()<32)throw new IllegalStateException("INTEGRATION_TOKEN至少32个字符");
  var filter=new OncePerRequestFilter(){
   @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
    String header=Objects.requireNonNullElse(request.getHeader("Authorization"),"");
    boolean valid=!token.isBlank()&&header.startsWith("Bearer ")&&MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),header.substring(7).getBytes(StandardCharsets.UTF_8));
    if(!valid){response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\"INTEGRATION_AUTH_REQUIRED\",\"message\":\"接口身份未配置或认证失败\"}");return;}
    long requestLimit=Objects.requireNonNullElse(request.getContentType(),"").startsWith("multipart/")?51L*1024*1024:50L*1024*1024;
    if(request.getContentLengthLong()>requestLimit){response.setStatus(413);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\"FILE_SIZE\",\"message\":\"请求超过50MB\"}");return;}
    var roles=new ArrayList<SimpleGrantedAuthority>();roles.add(new SimpleGrantedAuthority("ROLE_INTEGRATION"));if(canPublish)roles.add(new SimpleGrantedAuthority("ROLE_INTEGRATION_PUBLISH"));
    var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken("integration",null,roles));SecurityContextHolder.setContext(context);
    chain.doFilter(request,response);
   }
  };
  return http.securityMatcher("/api/integration/v1/**")
   .addFilterBefore(filter,BasicAuthenticationFilter.class)
   .authorizeHttpRequests(a->a.anyRequest().hasRole("INTEGRATION"))
   .csrf(c->c.disable()).httpBasic(c->c.disable()).formLogin(c->c.disable()).requestCache(c->c.disable())
   .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .build();
 }
}
