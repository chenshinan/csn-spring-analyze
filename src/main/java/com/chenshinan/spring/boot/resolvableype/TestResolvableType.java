package com.chenshinan.spring.boot.resolvableype;

import org.springframework.core.GenericTypeResolver;

/**
 * @author shinan.chen
 * @since 2019/3/27
 */
public class TestResolvableType {
    public static void main(String[] args) {
        RI ri = new RImpl();
        /**
         * 获取接口的唯一泛型
         */
        Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(
                ri.getClass(), RI.class);
        System.out.println(requiredType);
    }
}
