package com.lin.linagent.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.lin.linagent.contant.CommonVariables;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.io.File;

/**
 * 自定义elasticsearch配置
 */
@Configuration
public class ElasticSearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient() throws Exception {
        //准备HTTPS证书
        File certFile = new File(CommonVariables.ELASTIC_CA_CRT);
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null,(x509Certificates, s) -> true)
                .build();
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        //设置账号密码
        credentialsProvider.setCredentials(AuthScope.ANY,new UsernamePasswordCredentials(CommonVariables.ELASTIC_USER,CommonVariables.ELASTIC_PASSWORD));

        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200,"https")
        ).setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        //设置账号密码
                        .setDefaultCredentialsProvider(credentialsProvider)
                        //配置ssl证书
                        .setSSLContext(sslContext))
                .build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
