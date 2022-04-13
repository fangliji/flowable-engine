package org.flowable.common.engine.impl.persistence.cache;

/**
 * 类名称：ProcessCache
 * 类描述：TODO
 * 创建时间：4/12/22 6:56 PM
 * 创建人：flj
 */
public interface ProcessCache<T> {
    T get(String id);

    boolean contains(String id);

    void add(String id, T object);

    void remove(String id);

    void clear();
}
