package com.chenshinan.spring.boot.bean;

import org.springframework.stereotype.Component;

/**
 * @author shinan.chen
 * @since 2019/3/31
 */
@Component
public class BeanCsn {
    public BeanCsn() {
        System.out.println("BeanCsn我被初始化了");
    }
}
