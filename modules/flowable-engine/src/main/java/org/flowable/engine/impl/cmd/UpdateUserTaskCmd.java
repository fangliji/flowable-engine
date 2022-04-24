package org.flowable.engine.impl.cmd;

import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.bpmn.behavior.FlowNodeActivityBehavior;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.dynamic.DynamicUpdateUserTaskBuilder;
import org.flowable.engine.impl.dynamic.DynamicUserTaskBuilder;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

import java.io.Serializable;
import java.util.List;

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
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId,true);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(dynamicUpdateUserTaskBuilder.getTaskKey());
        if (flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            if (Integer.valueOf(0).equals(dynamicUpdateUserTaskBuilder.getFlag())) {
               List<String> candidateUsers = userTask.getCandidateUsers();
               if (candidateUsers!=null) {
                   candidateUsers.addAll(dynamicUpdateUserTaskBuilder.getCandidateUsers());
               }
            } else {
                userTask.setCandidateUsers(dynamicUpdateUserTaskBuilder.getCandidateUsers());
            }
            userTask.setEditFlag("1");
            try {
                ProcessDefinitionUtil.updateProcess(processInstanceId, bpmnModel);
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("updateUserTaskCmd"+e.getCause().toString());
            }
            // 如果是当前正在执行的节点编辑，则需要往下走编辑人员的接口
            List<TaskEntity> taskEntities = CommandContextUtil.getTaskService(commandContext).findTasksByProcessInstanceId(processInstanceId);
            String excutionId = null;
            String taskId = null;
            TaskEntity currentTaskEntity = null;
            for (TaskEntity taskEntity:taskEntities) {
                // 删除了，重新生成审批人
                if (dynamicUpdateUserTaskBuilder.getTaskKey().equals(taskEntity.getTaskDefinitionKey())) {
                    taskId = taskEntity.getId();
                    excutionId = taskEntity.getExecutionId();
                    break;
                }
            }
            if (excutionId!=null) {
                // 具体的逻辑，流给子类行为处理，不同的情况下处理行为不同
                ExecutionEntity executionEntity = CommandContextUtil.getExecutionEntityManager(commandContext).findById(excutionId);
                if (flowElement instanceof FlowNode) {
                    ActivityBehavior activityBehavior = (ActivityBehavior) ((UserTask) flowElement).getBehavior();
                    FlowNodeActivityBehavior flowNodeActivityBehavior = (FlowNodeActivityBehavior) activityBehavior;
                    flowNodeActivityBehavior.updateFlowTask(executionEntity,userTask);
                }
            }
            // 当前流程审批人编辑判断 TODO:如果编辑的是当前进行中的流程节点，需要生效处理
        } else {
            throw new FlowableIllegalArgumentException("该节点不支持编辑："+dynamicUpdateUserTaskBuilder.getTaskKey());
        }
        return null;
    }
}
