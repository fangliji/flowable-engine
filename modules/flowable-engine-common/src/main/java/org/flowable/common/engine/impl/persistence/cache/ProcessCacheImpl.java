package org.flowable.common.engine.impl.persistence.cache;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 类名称：ProcessCacheImpl
 * 类描述：TODO 再考虑一下
 * 创建时间：4/12/22 6:57 PM
 * 创建人：flj
 */
/*public class ProcessCacheImpl<T> implements ProcessCache<T> {*/

   /* public static final String PREFIX = "*";
    public static final String PREFIXKEY = "pangu_";
    private RedisTemplate redisTemplate;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCacheImpl.class);


    public RedisTemplate getRedisTemplate() {

        return redisTemplate;
    }

    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public T get(String id) {
        Object obj=null;
        if (StringUtils.isNotEmpty(id)){
            obj = redisTemplate.opsForHash().get(PREFIXKEY+id,PREFIXKEY+ id);
        }
        return obj;
    }

    public boolean contains(String id){
       return false;
    }

    public void add(String id, T object) {

    }

    public void remove(String id) {

    }

    public void clear() {

    }*/
/*}*/
