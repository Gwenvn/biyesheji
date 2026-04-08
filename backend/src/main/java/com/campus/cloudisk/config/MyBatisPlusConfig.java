package com.campus.cloudisk.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 包含：
 * <ol>
 *   <li>分页插件（PaginationInnerInterceptor）：为 selectPage 提供物理分页支持</li>
 *   <li>自动填充处理器（MetaObjectHandler）：自动填充 createTime / updateTime 字段</li>
 * </ol>
 * </p>
 *
 * @author campus-cloud
 * @since 2024-01-01
 */
@Slf4j
@Configuration
public class MyBatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 分页插件
     * <p>
     * 必须注册此插件，否则 Mapper.selectPage() 不会进行物理分页，
     * 将返回全部数据（性能问题）。
     * DbType.MYSQL 表示数据库类型为 MySQL，插件会生成对应的 LIMIT 语句。
     * </p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加 MySQL 分页插件（如切换数据库类型需修改 DbType）
        interceptor.addInnerInterceptor(
            new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL)
        );
        return interceptor;
    }

    /**
     * 自动填充处理器
     * <p>
     * 对实体类中标注了 {@code @TableField(fill = FieldFill.INSERT)} 或
     * {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 的字段自动赋值，
     * 无需在业务代码中手动 set。
     * <br/>
     * 对应字段：
     * <ul>
     *   <li>createTime — INSERT 时填充当前时间</li>
     *   <li>updateTime — INSERT 和 UPDATE 时都填充当前时间</li>
     * </ul>
     * </p>
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 插入时自动填充（INSERT SQL 执行前触发）
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                // 填充 createTime（仅在字段值为 null 时才填充，避免覆盖手动设置的值）
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                // 填充 updateTime
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            /**
             * 更新时自动填充（UPDATE SQL 执行前触发）
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                // 每次更新都刷新 updateTime 为当前时间
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
