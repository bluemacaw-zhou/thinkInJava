package io.bluemacaw.concurrency.reorder;

import lombok.Data;

@Data
public class Point {
    // 去掉volatile后性能提升
    // -XX:-RestrictContended
    // @Contended
    private volatile long x;
    // private long p1, p2, p3, p4, p5, p6, p7; // 字节填充后 性能提升
    private volatile long y;

    public void increaseX() {
        x++;
    }

    public void increaseY() {
        y++;
    }
}
