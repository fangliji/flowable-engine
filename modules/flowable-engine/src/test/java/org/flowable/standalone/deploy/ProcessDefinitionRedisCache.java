package org.flowable.standalone.deploy;

import org.flowable.engine.impl.persistence.deploy.ProcessDefinitionCacheEntry;
import org.flowable.common.engine.impl.persistence.deploy.DeploymentCache;

/**
 * 类名称：ProcessDefinitionRedisCache 需要将加签的代码实现到网关中
 * 类描述：TODO
 * 创建时间：2/22/22 8:39 PM
 * 创建人：flj
 */
public class ProcessDefinitionRedisCache implements DeploymentCache<ProcessDefinitionCacheEntry> {
    // 按流程实例id获取，流程实例id没有，就用流程定义id去获取，目的是为了实现

    @Override
    public ProcessDefinitionCacheEntry get(String id) {
        return null;
    }

    @Override
    public boolean contains(String id) {
        return false;
    }

    @Override
    public void add(String id, ProcessDefinitionCacheEntry object) {

    }

    @Override
    public void remove(String id) {

    }

    @Override
    public void clear() {

    }
}
