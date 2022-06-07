package com.niezhiliang.nacos.refresh.autoconfigure;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import com.sun.org.slf4j.internal.Logger;
import com.sun.org.slf4j.internal.LoggerFactory;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author niezhiliang
 * @version v 0.0.1
 * @date 2022/6/7 17:31
 */
public class NacosRefreshAnnotationPostProcess extends AbstractAnnotationBeanPostProcessor implements EnvironmentAware {

    //, ApplicationListener<NacosConfigReceivedEvent>

    private static final String NACOS_PROPERTY_SOURCE_CLASS_NAME = "com.alibaba.nacos.spring.core.env.NacosPropertySource";

    private final static Map<String, List<FieldInstance>> placeholderValueTargetMap = new HashMap<>();


    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 当前Nacos环境配置
     */
    private Map<String, Object> currentPlaceholderConfigMap = new ConcurrentHashMap<>();

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes annotationAttributes, Object o, String s, Class<?> aClass,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        return annotationAttributes.get("value");
    }

    public NacosRefreshAnnotationPostProcess() {
        super(Value.class, NacosValue.class);
    }

    @Override
    protected String buildInjectedObjectCacheKey(AnnotationAttributes annotationAttributes, Object o, String s,
                                                 Class<?> aClass, InjectionMetadata.InjectedElement injectedElement) {
        Field field = (Field)injectedElement.getMember();
        String key = null;
        if (field.isAnnotationPresent(Value.class)) {

            key = field.getAnnotation(Value.class).value();
        } else if (field.isAnnotationPresent(NacosValue.class)) {
            key = field.getAnnotation(NacosValue.class).value();
        }
        int startIndex = key.indexOf("${");
        int endIndex = findPlaceholderEndIndex(key, startIndex);
        key = key.substring(startIndex + 2, endIndex);
        addFieldInstance(key, field, o);
        return injectedElement.getMember().getName();
    }

    @Override
    public void setEnvironment(Environment environment) {
        StandardEnvironment standardEnvironment = (StandardEnvironment)environment;
        for (PropertySource<?> propertySource : standardEnvironment.getPropertySources()) {
            if (propertySource.getClass().getName().equals(NACOS_PROPERTY_SOURCE_CLASS_NAME)) {
                MapPropertySource mapPropertySource = (MapPropertySource)propertySource;
                for (String propertyName : mapPropertySource.getPropertyNames()) {
                    currentPlaceholderConfigMap.put(propertyName, mapPropertySource.getProperty(propertyName));
                }
            }
        }
    }


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
            Map<String, ConfigChangeItem> stringConfigChangeItemMap = filterChangeData(currentPlaceholderConfigMap, newConfigMap);
            for (Object o : stringConfigChangeItemMap.keySet()) {
                String key = (String)o;
                ConfigChangeItem o1 = stringConfigChangeItemMap.get(o);
                updateFieldValue(key, o1.getNewValue());
            }
        } finally {
            currentPlaceholderConfigMap = newConfigMap;
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
            if (org.apache.commons.lang3.StringUtils.isNotBlank(path)) {
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

    private static class FieldInstance {
        final Object o;

        final Field field;

        public FieldInstance(Object o, Field field) {
            this.o = o;
            this.field = field;
        }
    }

    private void addFieldInstance(String key, Field field, Object o) {
        List<FieldInstance> fieldInstances = placeholderValueTargetMap.get(key);
        if (CollectionUtils.isEmpty(fieldInstances)) {
            fieldInstances = new ArrayList<>();
        }
        fieldInstances.add(new FieldInstance(o, field));
        placeholderValueTargetMap.put(key, fieldInstances);
    }


    private void updateFieldValue(String key, Object value) {
        List<FieldInstance> fieldInstances = placeholderValueTargetMap.get(key);
        for (FieldInstance fieldInstance : fieldInstances) {
            try {
                ReflectionUtils.makeAccessible(fieldInstance.field);
                fieldInstance.field.set(fieldInstance.o, value);
            } catch (Throwable e) {
                if (logger.isDebugEnabled()) {
                    logger.error(
                            "Can't update value of the " + fieldInstance.field.getName() + " (field) in "
                                    + fieldInstance.o.getClass().getSimpleName()+ " (bean)", e);
                }
            }

        }
    }

    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        // ${长度
        int index = startIndex + 2;
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (StringUtils.substringMatch(buf, index, "}")) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + 1;
                } else {
                    return index;
                }
            } else if (StringUtils.substringMatch(buf, index, "{")) {
                withinNestedPlaceholder++;
                index = index + 1;
            } else {
                index++;
            }
        }
        return -1;
    }
}
