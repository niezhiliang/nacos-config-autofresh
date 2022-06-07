package com.niezhiliang.nacos.refresh.autoconfigure;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import com.niezhiliang.nacos.refresh.autoconfigure.holder.RefreshHolder;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;

/**
 * @author niezhiliang
 * @version v 0.0.1
 * @date 2022/6/7 17:31
 */
public class NacosRefreshAnnotationPostProcess extends AbstractAnnotationBeanPostProcessor {

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
        RefreshHolder.addFieldInstance(key, field, o);
        return injectedElement.getMember().getName();
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
