package org.flowable.engine.impl.dynamic;

/**
 * 类名称：DynamicUpgradeProcessInstanceBuilder
 * 类描述：TODO
 * 创建时间：4/29/22 2:20 PM
 * 创建人：flj
 */
public class DynamicUpgradeProcessInstanceBuilder {
    protected String processInstanceId;// 现有流程实例id
    protected String processDefinitionId;// 需要升级的流程实例id

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }
}
