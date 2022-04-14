package org.flowable.engine.impl.cmd;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.dynamic.DynamicUpdateUserTaskBuilder;
import org.flowable.engine.impl.dynamic.DynamicUserTaskBuilder;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

import java.io.Serializable;

/**
 * 类名称：UpdateUserTaskCmd
 * 类描述：TODO
 * 创建时间：4/14/22 10:36 AM
 * 创建人：flj
 */
public class UpdateUserTaskCmd implements Command<Void>, Serializable {
    protected String processInstanceId;
    protected String processDefinitionId;
    protected DynamicUpdateUserTaskBuilder dynamicUpdateUserTaskBuilder;

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

    public DynamicUpdateUserTaskBuilder getDynamicUpdateUserTaskBuilder() {
        return dynamicUpdateUserTaskBuilder;
    }

    public void setDynamicUpdateUserTaskBuilder(DynamicUpdateUserTaskBuilder dynamicUpdateUserTaskBuilder) {
        this.dynamicUpdateUserTaskBuilder = dynamicUpdateUserTaskBuilder;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(dynamicUpdateUserTaskBuilder.getId());
        if (flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            userTask.setCandidateUsers(dynamicUpdateUserTaskBuilder.getCandidateUsers());
            ProcessDefinitionUtil.updateProcess(processInstanceId,bpmnModel);
            // 当前流程审批人编辑判断 TODO:如果编辑的是当前进行中的流程节点，需要生效处理
        } else {
            throw new FlowableIllegalArgumentException("该节点不支持编辑："+dynamicUpdateUserTaskBuilder.getId());
        }
        return null;
    }
}
