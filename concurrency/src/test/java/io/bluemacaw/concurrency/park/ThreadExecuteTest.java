package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.concurrent.*;

@Slf4j
public class ThreadExecuteTest {
    @Test
    public void runnableTest() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("通过Runnable方式执行任务");
            }
        };

        new Thread(runnable).start(); // 通过启动线程回调执行run方法 有线程资源
//        new Thread(runnable).run(); // 只是调用对象的方法 没有线程资源
    }

    @Test
    public void futureTaskTest() {
        FutureTask<String> task = new FutureTask<String>(() -> {
            log.info("通过Callable方式执行任务");
            Thread.sleep(3000);
            return "返回任务结果";
        });

        new Thread(task).start();

        try {
            log.info(task.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void futureTest() {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<String> future1 = executor.submit(() -> {
            log.info("开始煮饭");

            try {
                Thread.sleep(3000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return "饭熟了";
        });

        Future<String> future2 = executor.submit(() -> {
            log.info("开始做菜");

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return "菜好了";
        });

        try {
            log.info("{}, {}, 开始吃饭...", future1.get(), future2.get());
        } catch (Exception e) {
            e.printStackTrace();
        }

        executor.shutdown();
    }
}
