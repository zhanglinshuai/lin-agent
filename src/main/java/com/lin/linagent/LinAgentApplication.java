package com.lin.linagent;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {PgVectorStoreAutoConfiguration.class})
public class LinAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinAgentApplication.class, args);
    }

}
