package ai.chat2db.gateway.start;

import ai.chat2db.gateway.start.config.GatewayProperties;
import ai.chat2db.gateway.start.config.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
        scanBasePackages = "ai.chat2db.gateway",
        excludeName = {
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@EnableConfigurationProperties({GatewayProperties.class, MilvusProperties.class})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
