package com.niezhiliang.nacos.refresh.sample.controller;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author niezhiliang
 * @version v
 * @date 2022/6/7 17:55
 */
@RestController
public class RefreshController {

    @Value("${spring.application.name}")
    public String valueName;

    @NacosValue("${spring.application.name}")
    public String nacosValueName;

    @GetMapping
    public String get() {

        return "@Value = " + valueName + " -- @NacosValue = " + nacosValueName;
    }
}
