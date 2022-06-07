package com.niezhiliang.nacos.refresh.autoconfigure;

import com.niezhiliang.nacos.refresh.autoconfigure.listener.RefreshListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author niezhiliang
 * @version v 0.0.1
 * @date 2022/6/7 17:26
 */
@Configuration
@ConditionalOnProperty(prefix = "nacos.config",name = "auto-refresh",havingValue = "true")
public class NacosRefreshAutoConfiguration {

    @Bean
    public NacosRefreshAnnotationPostProcess nacosRefreshAnnotationPostProcess() {
        return new NacosRefreshAnnotationPostProcess();
    }

    @Bean
    public RefreshListener refreshListener() {
        return new RefreshListener();
    }
}
