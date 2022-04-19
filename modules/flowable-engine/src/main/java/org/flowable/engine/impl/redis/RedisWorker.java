package org.flowable.engine.impl.redis;

import java.util.concurrent.TimeUnit;

/**
 * 类名称：RedisWorker
 * 类描述：TODO
 * 创建时间：4/18/22 10:03 AM
 * 创建人：flj
 */
public interface RedisWorker  {
    String get(String var1) throws Exception;
    Boolean setIfAbsent(String key,long seconds, String value) throws Exception;
    Boolean expire(String key, int timeout) throws Exception;
    void delete(String key) throws Exception;
}
