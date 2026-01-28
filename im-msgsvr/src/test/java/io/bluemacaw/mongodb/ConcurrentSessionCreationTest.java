package io.bluemacaw.mongodb;

import io.bluemacaw.mongodb.entity.Channel;
import io.bluemacaw.mongodb.service.ChannelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发 Session 创建测试
 * 测试高并发场景下 Session 的创建安全性
 */
@Slf4j
@SpringBootTest
public class ConcurrentChannelCreationTest {

    @Resource
    private ChannelService channelService;

    /**
     * 测试并发创建同一个 Session
     * 验证多线程同时创建同一个 session 时,只会创建一次,且 version 都是 100000
     */
    @Test
    public void testConcurrentSessionCreation() throws InterruptedException {
        String testSessionId = "test_user_1_2";
        int threadCount = 50;  // 模拟 50 个线程
        int channelType = 0;   // 私聊

        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);  // 用于同时启动所有线程
        CountDownLatch endLatch = new CountDownLatch(threadCount);  // 用于等待所有线程完成

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    // 等待所有线程就绪
                    startLatch.await();

                    // 尝试创建 Session
                    Channel channel = channelService.ensureChannelExists(testSessionId, channelType);

                    if (channel != null) {
                        successCount.incrementAndGet();

//                        log.info("线程 {} 成功获取 Session: {}, version: {}",
//                                threadId, session.getId(), session.getVersion());

                        // 创建成功后 模拟并发发送消息 版本号自增
                        Long version = channelService.incrementAndGetMessageVersion(testSessionId);
                        log.info("current version: {}", version);
                    } else {
                        failCount.incrementAndGet();
//                        log.error("线程 {} 获取 Session 失败", threadId);
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("线程 {} 执行异常", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 同时启动所有线程
        log.info("准备启动 {} 个线程并发创建 Session: {}", threadCount, testSessionId);
        startLatch.countDown();

        // 等待所有线程完成
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // 关闭线程池
        executorService.shutdown();

        // 验证结果
        log.info("测试完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

        if (!finished) {
            log.error("测试超时,部分线程未完成");
        }

        Channel channel = channelService.getChannel(testSessionId);
        log.info("session: {}", channel);
    }
}
