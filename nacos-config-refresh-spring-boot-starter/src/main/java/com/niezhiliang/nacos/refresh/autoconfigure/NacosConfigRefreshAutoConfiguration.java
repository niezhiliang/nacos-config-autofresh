package com.niezhiliang.nacos.refresh.autoconfigure;

import com.niezhiliang.nacos.refresh.autoconfigure.postprocess.ValueAnnotationBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.niezhiliang.nacos.refresh.autoconfigure.postprocess.NacosConfigRefreshAnnotationPostProcess;

/**
 * @author niezhiliang
 * @version v0.0.1
 * @date 2022/6/7 17:26
 */
@Configuration
@ConditionalOnProperty(prefix = "nacos.config", name = "auto-refresh", havingValue = "true")
public class NacosConfigRefreshAutoConfiguration {

    //@Bean
    public NacosConfigRefreshAnnotationPostProcess nacosRefreshAnnotationPostProcess() {
        return new NacosConfigRefreshAnnotationPostProcess();
    }


    @Bean
    public ValueAnnotationBeanPostProcessor valueAnnotationBeanPostProcessor() {
        return new ValueAnnotationBeanPostProcessor();
    }
}
