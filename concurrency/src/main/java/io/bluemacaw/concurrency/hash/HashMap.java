package io.bluemacaw.concurrency.hash;


public class HashMap<K, V> implements Map<K, V> {
    private Entry<K, V>[] table = null;
    private int size = 0;

    public HashMap() {
        table = new Entry[16];
    }

    @Override
    public V put(K k, V v) {
        int index = hash(k);
        Entry entry = table[index];

        if (null == entry) {
            table[index] = new Entry(k, v, index, null);
        } else {
            table[index] = new Entry(k, v, index, entry); // 头部插入
        }

        return table[index].getValue();
    }

    @Override
    public V get(K k) {
        if (size == 0) {
            return null;
        }

        int index = hash(k);
        Entry<K, V> entry = findValue(k, table[index]);
        return entry == null ? null : entry.getValue();
    }

    // TODO: size维护的不对
    @Override
    public int size() {
        return size;
    }

    public int hash(K k) {
        int i = k.hashCode() % 16;
        return i >= 0 ? i : -i;
    }

    private Entry<K, V> findValue(K k, Entry<K, V> entry) {
        if (k.equals(entry.getKey()) || k == entry.getKey()) {
            return entry;
        }
        else {
            if (entry.next != null) {
                return findValue(k, entry.next);
            }
        }
        return null;
    }
}
