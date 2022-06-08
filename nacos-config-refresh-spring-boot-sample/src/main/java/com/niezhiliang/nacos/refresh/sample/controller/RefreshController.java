package com.niezhiliang.nacos.refresh.sample.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.nacos.api.config.annotation.NacosValue;

/**
 * @author niezhiliang
 * @version v
 * @date 2022/6/7 17:55
 */
@RestController
public class RefreshController implements EnvironmentAware {

    @Value("${spring.application.name}")
    public String valueName;

    @NacosValue("${spring.application.name}")
    public String nacosValueName;

    private Environment environment;

    @GetMapping
    public String get() {

        return "@Value = " + valueName + " -- @NacosValue = " + nacosValueName + " environment: "
            + environment.getProperty("spring.application.name");
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
