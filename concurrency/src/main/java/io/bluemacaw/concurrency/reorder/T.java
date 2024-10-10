package io.bluemacaw.concurrency.reorder;

import lombok.Data;

@Data
public class T {
    private final int x;
    private int y;

    public T() {
        this.x = 3;
        this.y = 4;
    }
}
