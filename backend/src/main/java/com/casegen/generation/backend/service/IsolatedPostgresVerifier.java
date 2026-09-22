package com.casegen.generation.backend.service;
import java.util.Map;import java.util.UUID;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Propagation;import org.springframework.transaction.annotation.Transactional;
@Component public class IsolatedPostgresVerifier {
 private final JdbcTemplate jdbc;private final String url,user,password;
 public IsolatedPostgresVerifier(JdbcTemplate jdbc,@Value("${spring.datasource.url}")String url,@Value("${spring.datasource.username}")String user,@Value("${spring.datasource.password}")String password){this.jdbc=jdbc;this.url=url;this.user=user;this.password=password;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public VerificationDatabase create(UUID run){String schema="generation_"+run.toString().replace("-","");if(!schema.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid generated schema");jdbc.execute("CREATE SCHEMA "+schema);return new VerificationDatabase(schema,Map.of("SPRING_DATASOURCE_URL",url+(url.contains("?")?"&":"?")+"currentSchema="+schema,"SPRING_DATASOURCE_USERNAME",user,"SPRING_DATASOURCE_PASSWORD",password,"SERVER_PORT","0"));}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void remove(VerificationDatabase db){if(!db.schema().matches("generation_[a-f0-9]{32}"))throw new IllegalArgumentException("Unsafe generated schema");jdbc.execute("DROP SCHEMA "+db.schema()+" CASCADE");}
 public record VerificationDatabase(String schema,Map<String,String> environment){}
}
