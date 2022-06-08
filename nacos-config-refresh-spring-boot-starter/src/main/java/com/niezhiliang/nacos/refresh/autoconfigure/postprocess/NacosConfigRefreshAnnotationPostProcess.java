package com.niezhiliang.nacos.refresh.autoconfigure.postprocess;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.niezhiliang.nacos.refresh.autoconfigure.utils.NacosConfigPaserUtils;
import com.niezhiliang.nacos.refresh.autoconfigure.utils.PlaceholderUtils;

/**
 * @author niezhiliang
 * @version v0.0.1
 * @date 2022/6/7 17:31
 */
public class NacosConfigRefreshAnnotationPostProcess extends AbstractAnnotationBeanPostProcessor
    implements EnvironmentAware {

    private final Logger logger = Logger.getLogger(NacosConfigRefreshAnnotationPostProcess.class.getName());

    /**
     * nacos对应的propertySource的类名称
     */
    private static final String NACOS_PROPERTY_SOURCE_CLASS_NAME =
        "com.alibaba.nacos.spring.core.env.NacosPropertySource";

    /**
     * nacos配置文件类型
     */
    private static final String NACOS_CONFIG_TYPE = "nacos.config.type";

    /**
     * nacos配置data-id的占位符
     */
    private static final String NACOS_DATA_ID_PLACEHOLDER = "${nacos.config.data-id}";

    /**
     * spring环境对象
     */
    private StandardEnvironment standardEnvironment;

    /**
     * 存放被@Value和@NacosValue修饰的属性 key = 占位符 value = 属性集合
     */
    private final static Map<String, List<FieldInstance>> placeholderValueTargetMap = new HashMap<>();

    /**
     * 当前Nacos环境配置，第一次从环境对象中获取，后续变更后会被新的属性覆盖
     */
    private Map<String, Object> currentPlaceholderConfigMap = new ConcurrentHashMap<>();

    /**
     * 类型转换服务
     */
    private ConversionService conversionService = new DefaultConversionService();

    public NacosConfigRefreshAnnotationPostProcess() {
        super(Value.class, NacosValue.class);
    }

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes annotationAttributes, Object o, String s, Class<?> aClass,
        InjectionMetadata.InjectedElement injectedElement) throws Exception {
        String key = (String)annotationAttributes.get("value");
        key = PlaceholderUtils.parseStringValue(key, standardEnvironment, null);

        Field field = (Field)injectedElement.getMember();
        // 属性记录到缓存中
        addFieldInstance(key, field, o);
        Object value = currentPlaceholderConfigMap.get(key);
        return conversionService.convert(value, field.getType());
    }

    @Override
    protected String buildInjectedObjectCacheKey(AnnotationAttributes annotationAttributes, Object o, String s,
        Class<?> aClass, InjectionMetadata.InjectedElement injectedElement) {
        return o.getClass().getName() + "#" + injectedElement.getMember().getName();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.standardEnvironment = (StandardEnvironment)environment;
        for (PropertySource<?> propertySource : standardEnvironment.getPropertySources()) {
            // 筛选出nacos的配置
            if (propertySource.getClass().getName().equals(NACOS_PROPERTY_SOURCE_CLASS_NAME)) {
                MapPropertySource mapPropertySource = (MapPropertySource)propertySource;
                // 配置以键值对形式存储到当前属性配置集合中
                for (String propertyName : mapPropertySource.getPropertyNames()) {
                    currentPlaceholderConfigMap.put(propertyName, mapPropertySource.getProperty(propertyName));
                }
            }
        }
    }

    /**
     * 该注解支持占位符解析， 需要在拉取完配置后将nacos配置的data-id放到environment对象中 监听nacos配置更新操作
     * 
     * @param newContent
     * @throws Exception
     */
    @NacosConfigListener(dataId = NACOS_DATA_ID_PLACEHOLDER)
    public void onChange(String newContent) throws Exception {
        // 将配置内容解析成键值对
        Map<String, Object> newConfigMap = parseNacosConfigContext(newContent);

        try {
            // 刷新变更对象的值
            refreshTargetObjectFieldValue(newConfigMap);
        } finally {
            // 当前配置指向最新的配置
            currentPlaceholderConfigMap = newConfigMap;
        }
    }

    /**
     * 刷新变更对象的值
     * 
     * @param newConfigMap
     */
    private void refreshTargetObjectFieldValue(Map<String, Object> newConfigMap) {
        // 对比两次配置内容，筛选出变更后的配置项
        Map<String, ConfigChangeItem> stringConfigChangeItemMap =
            NacosConfigPaserUtils.filterChangeData(currentPlaceholderConfigMap, newConfigMap);

        // 反射给对象赋值
        for (String key : stringConfigChangeItemMap.keySet()) {
            ConfigChangeItem item = stringConfigChangeItemMap.get(key);
            // 嵌套占位符 防止中途嵌套中的配置变了 导致对象属性刷新失败
            if (placeholderValueTargetMap.containsKey(item.getOldValue())) {
                List<FieldInstance> fieldInstances = placeholderValueTargetMap.get(item.getOldValue());
                placeholderValueTargetMap.put(item.getNewValue(), fieldInstances);
                placeholderValueTargetMap.remove(item.getOldValue());
            }
            updateFieldValue(key, item.getNewValue(), item.getOldValue());
        }
    }

    /**
     * 解析nacos的配置
     * 
     * @param newContent
     * @return
     * @throws Exception
     */
    private Map<String, Object> parseNacosConfigContext(String newContent) throws Exception {
        // 解析nacos推送的配置内容为键值对
        String type = standardEnvironment.getProperty(NACOS_CONFIG_TYPE);

        Map<String, Object> newConfigMap = new HashMap<>(16);
        if (ConfigType.YAML.getType().equals(type)) {
            newConfigMap = (new Yaml()).load(newContent);
        } else if (ConfigType.PROPERTIES.getType().equals(type)) {
            Properties newProps = new Properties();
            newProps.load(new StringReader(newContent));
            newConfigMap = new HashMap<>((Map)newProps);
        }
        // 筛选出正确的配置
        return NacosConfigPaserUtils.getFlattenedMap(newConfigMap);
    }

    private static class FieldInstance {
        final Object bean;

        final Field field;

        public FieldInstance(Object bean, Field field) {
            this.bean = bean;
            this.field = field;
        }
    }

    /**
     * 将被@Value和@NacosValue修饰的属性，以键值对的形式存放到当前属性配置集合中
     * 
     * @param key
     * @param field
     * @param bean
     */
    private void addFieldInstance(String key, Field field, Object bean) {
        List<FieldInstance> fieldInstances = placeholderValueTargetMap.get(key);
        if (CollectionUtils.isEmpty(fieldInstances)) {
            fieldInstances = new ArrayList<>();
        }
        fieldInstances.add(new FieldInstance(bean, field));
        placeholderValueTargetMap.put(key, fieldInstances);
    }

    /**
     * 反射修改变更的对象属性值
     * 
     * @param key
     * @param newValue
     */
    private void updateFieldValue(String key, String newValue, String oldValue) {
        List<FieldInstance> fieldInstances = placeholderValueTargetMap.get(key);
        for (FieldInstance fieldInstance : fieldInstances) {
            try {
                ReflectionUtils.makeAccessible(fieldInstance.field);
                // 类型转换
                Object value = conversionService.convert(newValue, fieldInstance.field.getType());
                fieldInstance.field.set(fieldInstance.bean, value);
            } catch (Throwable e) {
                logger.warning("Can't update value of the " + fieldInstance.field.getName() + " (field) in "
                    + fieldInstance.bean.getClass().getSimpleName() + " (bean)");
            }
            logger.info("Nacos-config-refresh: " + fieldInstance.bean.getClass().getSimpleName() + "#"
                + fieldInstance.field.getName() + " field value changed from [" + oldValue + "] to [" + newValue + "]");
        }
    }
}
