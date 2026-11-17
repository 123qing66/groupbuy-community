package com.groupbuy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关（C 负责，#22 退款扫描需要）。
 *
 * <p>单独放在这里开启，避免改动组长 A 的启动类 GroupbuyApplication。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
