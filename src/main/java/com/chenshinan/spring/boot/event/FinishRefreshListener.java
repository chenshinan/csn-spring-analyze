package com.chenshinan.spring.boot.event;

import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * @author shinan.chen
 * @since 2019/5/22
 */
@Component
public class FinishRefreshListener implements ApplicationListener<ContextRefreshedEvent> {
    /**
     * 接收到ContextRefreshedEvent事件
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("FinishRefreshListener接收到event：" + event);
        LogFactory.getLog(FinishRefreshListener.class).info("测试日志：完成");
    }
}
