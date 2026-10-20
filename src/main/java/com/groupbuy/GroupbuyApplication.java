package com.groupbuy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 *
 * <p>MapperScan 统一在这里声明，各人写 mapper 时不用再自己加注解。</p>
 */
@MapperScan("com.groupbuy.**.mapper")
@SpringBootApplication
public class GroupbuyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupbuyApplication.class, args);
    }
}
