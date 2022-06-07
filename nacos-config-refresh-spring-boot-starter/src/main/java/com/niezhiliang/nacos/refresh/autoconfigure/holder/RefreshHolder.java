//package com.niezhiliang.nacos.refresh.autoconfigure.holder;
//
//import org.springframework.util.CollectionUtils;
//
//import java.lang.reflect.Field;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * @author niezhiliang
// * @version v 0.0.1
// * @date 2022/6/1 16:31
// */
//public class RefreshHolder {
//
//    private final static Map<String, List<FieldInstance>> map = new HashMap<>();
//
//    static class FieldInstance {
//        Object o;
//
//        Field field;
//
//        public FieldInstance(Object o, Field field) {
//            this.o = o;
//            this.field = field;
//        }
//    }
//
//    public static void addFieldInstance(String key, Field field, Object o) {
//        List<FieldInstance> fieldInstances = map.get(key);
//        if (CollectionUtils.isEmpty(fieldInstances)) {
//            fieldInstances = new ArrayList<>();
//        }
//        fieldInstances.add(new FieldInstance(o, field));
//        map.put(key, fieldInstances);
//    }
//
//    public static void updateFieldValue(String key, Object value) {
//        try {
//            List<FieldInstance> fieldInstances = map.get(key);
//            for (FieldInstance fieldInstance : fieldInstances) {
//                fieldInstance.field.set(fieldInstance.o, value);
//            }
//        } catch (Exception e) {
//
//        }
//    }
//}
