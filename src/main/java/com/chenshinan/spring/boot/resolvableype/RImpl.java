package com.chenshinan.spring.boot.resolvableype;

/**
 * @author shinan.chen
 * @since 2019/3/27
 */
public class RImpl implements RI {
    @Override
    public void init(String test) {
        System.out.println("初始化RI");
    }
}
