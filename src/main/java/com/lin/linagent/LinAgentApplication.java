package com.lin.linagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(exclude =  DataSourceAutoConfiguration.class)
@MapperScan("com.lin.linagent.mapper")
public class LinAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinAgentApplication.class, args);

    }

}
