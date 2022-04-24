package org.flowable.engine.impl.cmd;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.bpmn.behavior.FlowNodeActivityBehavior;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

import java.io.Serializable;
import java.util.List;

/**
 * 类名称：DeleteUserTaskCmd
 * 类描述：TODO
 * 创建时间：4/14/22 1:55 PM
 * 创建人：flj
 */
public class DeleteUserTaskCmd implements Command<Void>, Serializable {
    protected String processInstanceId;
    protected String processDefinitionId;
    protected String taskKey;

    public DeleteUserTaskCmd(String processInstanceId, String processDefinitionId, String taskKey) {
        this.processInstanceId = processInstanceId;
        this.processDefinitionId = processDefinitionId;
        this.taskKey = taskKey;
    }

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

    @Override
    public Void execute(CommandContext commandContext) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId,true);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(taskKey);
        if (flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            // 不能修改链接线，而是标记删除
            userTask.setEditFlag("1");
            userTask.setDeleteFlag("1");
            userTask.setSkipExpression("${true}");
            try{
                ProcessDefinitionUtil.updateProcess(processInstanceId,bpmnModel);
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("节点删除异常："+e.getCause().toString());
            }

            //如果是删除进行中的节点，则要处理excution ，删除进行中的任务需要删除掉
            //处理当前的excution.task
            // 当前流程审批人编辑判断
            // 如果是当前正在执行的节点编辑，则需要往下走编辑人员的接口
            List<TaskEntity> taskEntities = CommandContextUtil.getTaskService(commandContext).findTasksByProcessInstanceId(processInstanceId);
            TaskEntity currentTaskEntity = null;
            for (TaskEntity taskEntity:taskEntities) {
                // 删除了，重新生成审批人
                if (taskKey.equals(taskEntity.getTaskDefinitionKey())) {
                    currentTaskEntity = taskEntity;
                    break;
                }
            }
            if (currentTaskEntity!=null) {
                // 如果删除的是当前节点，就要处理，当前的任务
                if (currentTaskEntity.getExecutionId()!=null) {
                    ExecutionEntity executionEntity = CommandContextUtil.getExecutionEntityManager(commandContext).findById(currentTaskEntity.getExecutionId());
                    if (flowElement instanceof FlowNode) {
                        ActivityBehavior activityBehavior = (ActivityBehavior) ((UserTask) flowElement).getBehavior();
                        FlowNodeActivityBehavior flowNodeActivityBehavior = (FlowNodeActivityBehavior) activityBehavior;
                        flowNodeActivityBehavior.deleteFlowTask(executionEntity);
                        flowNodeActivityBehavior.leave(executionEntity);
                    }
                }
            }
        } else {
            throw new FlowableIllegalArgumentException("该节点不能删除："+taskKey);
        }
        return null;
    }
}
