package mezlogo.mid.api.utils;

import java.util.Map;

public class Tuple<K, V> implements Map.Entry<K, V> {
    private final K key;
    private final V value;

    public Tuple(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public static <K, V> Tuple<K, V> of(K key, V value) {
        return new Tuple<>(key, value);
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(Object value) {
        throw new UnsupportedOperationException("This is immutable tuple");
    }

}
