package com.niezhiliang.nacos.refresh.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.niezhiliang.nacos.refresh.autoconfigure.postprocess.NacosConfigRefreshAnnotationPostProcess;

import java.util.logging.Logger;

/**
 * @author niezhiliang
 * @version v0.0.1
 * @date 2022/6/7 17:26
 */
@Configuration
@ConditionalOnProperty(prefix = "nacos.config", name = "auto-refresh", havingValue = "true")
public class NacosConfigRefreshAutoConfiguration {

    private final Logger logger = Logger.getLogger(NacosConfigRefreshAutoConfiguration.class.getName());

    @Bean
    public NacosConfigRefreshAnnotationPostProcess nacosRefreshAnnotationPostProcess() {
        logger.info("\n--------------------------------------------\nNacos-config-refresh-starter load successful" +
                "\n--------------------------------------------");
        return new NacosConfigRefreshAnnotationPostProcess();
    }
}
