package org.apache.ignite.configuration.internal;

import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.configuration.internal.property.Modifier;

/**
 * TODO: Add class description.
 *
 * @author @java.author
 * @version @java.version
 */
public abstract class DynamicConfiguration<T, CHANGE, INIT> implements Modifier<T> {

    protected final String qualifiedName;

    protected final String key;

    protected final Map<String, Modifier> members = new HashMap<>();

    protected DynamicConfiguration(String prefix, String key) {
        this.qualifiedName = String.format("%s.%s", prefix, key);
        this.key = key;
    }

    protected <M extends Modifier> M add(M member) {
        members.put(member.key(), member);

        return member;
    }

    public abstract void change(CHANGE change);

    public abstract void init(INIT init);

    @Override public void updateValue(String key, Object newValue) {
        key = nextPostfix(key);

        String key1 = nextKey(key);

        members.get(key1).updateValue(key, newValue);
    }

    private String nextKey(String key) {
        int of = key.indexOf('.');

        return of == -1 ? key : key.substring(0, of);
    }

    private String nextPostfix(String key) {
        String start = key.substring(0, key.indexOf('.'));
        if (!start.equals(this.key))
            throw new IllegalArgumentException();

        key = key.substring(this.key.length() + 1);

        return key;
    }

    @Override public Modifier<T> find(String key) {
        if (key.equals(this.key))
            return this;

        key = nextPostfix(key);

        return members.get(nextKey(key)).find(key);
    }

    @Override public String key() {
        return key;
    }
}
