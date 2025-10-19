package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

@Slf4j
public class CyclicBarrierTest2 {
    //保存每个学生的平均成绩
    private ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<String,Integer>();

    private ExecutorService threadPool = Executors.newFixedThreadPool(3);

    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(3,() -> {
        int result=0;
        Set<String> set = map.keySet();
        for(String s : set){
            result += map.get(s);
        }
        log.info("三人平均成绩为:{}分", (result / 3));
    });

    @Test
    public void mainTest() throws Exception {
        for(int i = 0; i < 3; i++){
            threadPool.execute(() -> {
                String threadName = Thread.currentThread().getName();

                //获取学生平均成绩
                int score= (int) (Math.random() * 40 + 60);
                map.put(threadName, score);
                log.info("{} 同学的成绩为：{}", threadName, score);
                try {
                    cyclicBarrier.await(); //执行完运行await(),等待所有学生平均成绩都计算完毕
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            });
        }

        Thread.sleep(3000);
    }
}
