package org.flowable.engine.impl.persistence.entity;

import java.io.Serializable;

/**
 * 类名称：ProcessEntityImpl
 * 类描述：TODO
 * 创建时间：4/12/22 5:08 PM
 * 创建人：flj
 */
public class ProcessEntityImpl extends AbstractBpmnEngineEntity implements ProcessEntity, Serializable {
    private static final long serialVersionUID = 1L;
    protected byte[] bytes;
    protected String procInstId;

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getProcInstId() {
        return procInstId;
    }


    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    @Override
    public Object getPersistentState() {
        return procInstId;
    }
}
