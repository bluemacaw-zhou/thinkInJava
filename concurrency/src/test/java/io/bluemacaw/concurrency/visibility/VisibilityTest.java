package io.bluemacaw.concurrency.visibility;

// 可见性测试案例
// 线程B 控制 线程A 的执行

// jmm内存模型 线程间共享内存模型
// CPU -- 本地内存(缓存) -- 主内存

import io.bluemacaw.concurrency.common.UnsafeFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

// 内存屏障的理解
// 缓存强制刷新
// 底层是汇编lock前缀 保证当前缓存立刻刷回主存 并通知变量的其它副本失效(缓存过期)
@Slf4j
public class VisibilityTest {
    // volatile在hotspot中的实现
    // bytecodeInterpreter.cpp
    private boolean flag = true; // 方法 1 volatile修饰 内存屏障
    // 方法 6 volatile修饰
    // 方法 7 Integer 最终count的值是final修饰的 final也会保证可见性
    private int count = 0;

    @Test
    public void mainTest() throws Exception {
        Thread threadA = new Thread(this::load, "Thread A");
        threadA.start();

        Thread.sleep(1000);

        Thread threadB = new Thread(this::refresh, "Thread B");
        threadB.start();
    }

    public void refresh() {
        // 线程B对flag的写操作会happens-before 线程A对flag的读操作
        flag = false;
        log.info("{} modify flag: {}", Thread.currentThread().getName(), flag);
    }

    // 可见性的实现方式
    // 内存屏障
    // 上下文切换
    public void load() {
        log.info("{} start...", Thread.currentThread().getName());

        while (flag) {
            count++;
            // log.info("count: {}", count);

            // 方法 2 内存屏障
            UnsafeFactory.getUnsafe().storeFence();

            // 方法 3 上下文切换(缓存过期) 从主存加载最新的值
//            Thread.yield(); // 释放时间片

            // 方法 4 依赖synchronized语义 底层是内存屏障
//            System.out.println(count);

            // 方法 5 内存屏障
//            LockSupport.unpark(Thread.currentThread());

            // 方法 6 内存屏障
//            try {
//                Thread.sleep(1);
//            } catch (Exception e) {
//                // do nothing
//            }
        }

        log.info("{} 跳出循环: count={}", Thread.currentThread().getName(), count);
    }
}
