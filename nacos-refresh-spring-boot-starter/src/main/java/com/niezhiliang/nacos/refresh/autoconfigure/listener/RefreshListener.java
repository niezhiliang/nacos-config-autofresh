package com.niezhiliang.nacos.refresh.autoconfigure.listener;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.niezhiliang.nacos.refresh.autoconfigure.holder.RefreshHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author niezhiliang
 * @version v
 * @date 2022/6/7 17:42
 */

public class RefreshListener implements EnvironmentAware {

    private static final String NACOS_PROPERTY_SOURCE_CLASS_NAME = "com.alibaba.nacos.spring.core.env.NacosPropertySource";

    /**
     * 当前Nacos环境配置
     */
    private Map<String, Object> currentConfigMap = new ConcurrentHashMap<>();

    /**
     * 该注解支持占位符解析， 需要在拉取完配置后将nacos配置的data-id放到environment对象中
     *
     * @param newContent
     * @throws Exception
     */
    @NacosConfigListener(dataId = "${nacos.config.data-id}")
    public void onChange(String newContent) throws Exception {
        Map<String, Object> newConfigMap = (new Yaml()).load(newContent);
        newConfigMap = getFlattenedMap(newConfigMap);
        try {
            Map<String, ConfigChangeItem> stringConfigChangeItemMap = filterChangeData(currentConfigMap, newConfigMap);
            for (Object o : stringConfigChangeItemMap.keySet()) {
                String key = (String)o;
                ConfigChangeItem o1 = stringConfigChangeItemMap.get(o);
                RefreshHolder.updateFieldValue(key, o1.getNewValue());
            }
        } finally {
            currentConfigMap = newConfigMap;
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        StandardEnvironment standardEnvironment = (StandardEnvironment)environment;
        for (PropertySource<?> propertySource : standardEnvironment.getPropertySources()) {
            if (propertySource.getClass().getName().equals(NACOS_PROPERTY_SOURCE_CLASS_NAME)) {
                MapPropertySource mapPropertySource = (MapPropertySource)propertySource;
                for (String propertyName : mapPropertySource.getPropertyNames()) {
                    currentConfigMap.put(propertyName, mapPropertySource.getProperty(propertyName));
                }
            }
        }
    }

    protected Map<String, ConfigChangeItem> filterChangeData(Map oldMap, Map newMap) {
        Map<String, ConfigChangeItem> result = new HashMap<String, ConfigChangeItem>(16);
        for (Iterator<Map.Entry<String, Object>> entryItr = oldMap.entrySet().iterator(); entryItr.hasNext();) {
            Map.Entry<String, Object> e = entryItr.next();
            ConfigChangeItem cci = null;
            if (newMap.containsKey(e.getKey())) {
                if (e.getValue().equals(newMap.get(e.getKey()))) {
                    continue;
                }
                cci = new ConfigChangeItem(e.getKey(), e.getValue().toString(), newMap.get(e.getKey()).toString());
                cci.setType(PropertyChangeType.MODIFIED);
            } else {
                cci = new ConfigChangeItem(e.getKey(), e.getValue().toString(), null);
                cci.setType(PropertyChangeType.DELETED);
            }

            result.put(e.getKey(), cci);
        }

        for (Iterator<Map.Entry<String, Object>> entryItr = newMap.entrySet().iterator(); entryItr.hasNext();) {
            Map.Entry<String, Object> e = entryItr.next();
            if (!oldMap.containsKey(e.getKey())) {
                ConfigChangeItem cci = new ConfigChangeItem(e.getKey(), null, e.getValue().toString());
                cci.setType(PropertyChangeType.ADDED);
                result.put(e.getKey(), cci);
            }
        }

        return result;
    }

    private final Map<String, Object> getFlattenedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(128);
        buildFlattenedMap(result, source, null);
        return result;
    }

    private void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Iterator<Map.Entry<String, Object>> itr = source.entrySet().iterator(); itr.hasNext();) {
            Map.Entry<String, Object> e = itr.next();
            String key = e.getKey();
            if (StringUtils.isNotBlank(path)) {
                if (e.getKey().startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + '.' + key;
                }
            }
            if (e.getValue() instanceof String) {
                result.put(key, e.getValue());
            } else if (e.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>)e.getValue();
                buildFlattenedMap(result, map, key);
            } else if (e.getValue() instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>)e.getValue();
                if (collection.isEmpty()) {
                    result.put(key, "");
                } else {
                    int count = 0;
                    for (Object object : collection) {
                        buildFlattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), key);
                    }
                }
            } else {
                result.put(key, (e.getValue() != null ? e.getValue() : ""));
            }
        }
    }
}
