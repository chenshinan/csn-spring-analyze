package com.chenshinan.spring.boot.resolvableype;

/**
 * @author shinan.chen
 * @since 2019/3/27
 */
public interface RI<C extends FI> {
    void init(String test);
}
