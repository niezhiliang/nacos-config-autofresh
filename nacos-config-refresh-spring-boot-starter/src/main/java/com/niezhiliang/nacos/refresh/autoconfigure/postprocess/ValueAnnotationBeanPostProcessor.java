package com.niezhiliang.nacos.refresh.autoconfigure.postprocess;

import static org.springframework.core.annotation.AnnotationUtils.getAnnotation;
import static org.springframework.util.SystemPropertyUtils.*;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import com.niezhiliang.nacos.refresh.autoconfigure.utils.MD5;

/**
 * @author haoxiaoyong
 * @date created at 下午4:18 on 2022/6/9
 * @github https://github.com/haoxiaoyong1014
 * @blog www.haoxiaoyong.cn
 */
public class ValueAnnotationBeanPostProcessor extends AbstractAnnotationBeanPostProcessor
    implements BeanFactoryAware, EnvironmentAware, ApplicationListener {

    /**
     * placeholder, nacosValueTarget
     */
    private final Map<String, List<ValueTarget>> placeholderValueTargetMap = new HashMap<>();

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;

    /**
     * nacos事件变更后推送事件
     */
    private static final String NACOS_CONFIG_REVEIVED_EVENT_CLASS_NAME =
        "com.alibaba.nacos.spring.context.event.config.NacosConfigReceivedEvent";

    public ValueAnnotationBeanPostProcessor() {
        super(Value.class);
    }

    @Override
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName,
        Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) throws Exception {
        String annotationValue = (String)attributes.get("value");
        String value = beanFactory.resolveEmbeddedValue(annotationValue);
        Member member = injectedElement.getMember();
        if (member instanceof Field) {
            return convertIfNecessary((Field)member, value);
        }
        return null;
    }

    @Override
    protected String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
        Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        return bean.getClass().getName() + "#" + injectedElement.getMember().getName();
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // 为了剔除nacos的依赖包
        if (!NACOS_CONFIG_REVEIVED_EVENT_CLASS_NAME.equals(event.getClass().getName())) {
            return;
        }
        for (Map.Entry<String, List<ValueTarget>> entry : placeholderValueTargetMap.entrySet()) {
            String key = environment.resolvePlaceholders(entry.getKey());
            String newValue = environment.getProperty(key);
            if (newValue == null) {
                continue;
            }
            List<ValueTarget> beanPropertyList = entry.getValue();
            for (ValueTarget target : beanPropertyList) {
                String md5String = MD5.getInstance().getMD5String(newValue);
                boolean isUpdate = !target.lastMD5.equals(md5String);
                if (isUpdate) {
                    target.updateLastMD5(md5String);
                    setField(target, newValue);
                }
            }
        }
    }

    private void setField(final ValueTarget valueTarget, final String propertyValue) {
        final Object bean = valueTarget.bean;

        Field field = valueTarget.field;
        // String fieldName = field.getName();
        try {
            ReflectionUtils.makeAccessible(field);
            field.set(bean, convertIfNecessary(field, propertyValue));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private Object convertIfNecessary(Field field, Object value) {
        TypeConverter converter = beanFactory.getTypeConverter();
        return converter.convertIfNecessary(value, field.getType(), field);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                "NacosValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory)beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        doWithFields(bean, beanName);

        return super.postProcessBeforeInitialization(bean, beanName);
    }

    private void doWithFields(final Object bean, final String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException {
                Value annotation = getAnnotation(field, Value.class);
                doWithAnnotation(beanName, bean, annotation, field.getModifiers(), null, field);
            }
        });
    }

    private void doWithAnnotation(String beanName, Object bean, Value annotation, int modifiers, Method method,
        Field field) {
        if (annotation != null) {
            if (Modifier.isStatic(modifiers)) {
                return;
            }
            String placeholder = resolvePlaceholder(annotation.value());

            if (placeholder == null) {
                return;
            }

            ValueTarget valueTarget = new ValueTarget(bean, beanName, method, field);
            put2ListMap(placeholderValueTargetMap, placeholder, valueTarget);
        }
    }

    private <K, V> void put2ListMap(Map<K, List<V>> map, K key, V value) {
        List<V> valueList = map.get(key);
        if (valueList == null) {
            valueList = new ArrayList<V>();
        }
        valueList.add(value);
        map.put(key, valueList);
    }

    private String resolvePlaceholder(String placeholder) {
        if (!placeholder.startsWith(PLACEHOLDER_PREFIX)) {
            return null;
        }

        if (!placeholder.endsWith(PLACEHOLDER_SUFFIX)) {
            return null;
        }

        if (placeholder.length() <= PLACEHOLDER_PREFIX.length() + PLACEHOLDER_SUFFIX.length()) {
            return null;
        }

        int beginIndex = PLACEHOLDER_PREFIX.length();
        int endIndex = placeholder.length() - PLACEHOLDER_PREFIX.length() + 1;
        placeholder = placeholder.substring(beginIndex, endIndex);

        int separatorIndex = placeholder.indexOf(VALUE_SEPARATOR);
        if (separatorIndex != -1) {
            return placeholder.substring(0, separatorIndex);
        }

        return placeholder;
    }

    private static class ValueTarget {

        private final Object bean;

        private final String beanName;

        private final Method method;

        private final Field field;

        private String lastMD5;

        ValueTarget(Object bean, String beanName, Method method, Field field) {
            this.bean = bean;

            this.beanName = beanName;

            this.method = method;

            this.field = field;

            this.lastMD5 = "";
        }

        protected void updateLastMD5(String newMD5) {
            this.lastMD5 = newMD5;
        }

    }
}
