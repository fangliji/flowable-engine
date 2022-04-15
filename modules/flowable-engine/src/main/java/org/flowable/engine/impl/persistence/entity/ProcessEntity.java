package org.flowable.engine.impl.persistence.entity;

import org.flowable.common.engine.impl.db.HasRevision;
import org.flowable.common.engine.impl.persistence.entity.Entity;
import org.flowable.engine.repository.Model;

/**
 * 类名称：ProcessEntity
 * 类描述：TODO
 * 创建时间：4/12/22 5:02 PM
 * 创建人：flj
 */
public interface ProcessEntity extends  Entity,HasRevision {
    byte[] getBytes();
    void setBytes(byte[] bytes);
    String getProcInstId();
    void setProcInstId(String procInstId);
}
