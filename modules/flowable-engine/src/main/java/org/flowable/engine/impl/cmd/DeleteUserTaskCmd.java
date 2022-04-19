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
    protected String taskDefKey;
    protected String taskId;

    public DeleteUserTaskCmd(String processInstanceId, String processDefinitionId, String taskDefKey, String taskId) {
        this.processInstanceId = processInstanceId;
        this.processDefinitionId = processDefinitionId;
        this.taskDefKey = taskDefKey;
        this.taskId = taskId;
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

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId,true);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(taskDefKey);
        if (flowElement instanceof UserTask) {
            UserTask userTask = (UserTask) flowElement;
            // 不能修改链接线，而是标记删除
            userTask.setEditFlag("1");
            userTask.setDeleteFlag("1");
            userTask.setSkipExpression("${true}");
            try{
                ProcessDefinitionUtil.updateProcess(processInstanceId,bpmnModel);
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("节点删除异常："+e.getMessage());
            }

            //如果是删除进行中的节点，则要处理excution ，删除进行中的任务需要删除掉
            //处理当前的excution.task
            // 当前流程审批人编辑判断
            if (StringUtils.isEmpty(taskId)) {
                return null;
            }

            TaskEntity task = CommandContextUtil.getTaskService(commandContext).getTask(taskId);
            if (task==null) {
                throw new FlowableIllegalArgumentException("节点删除异常：当前任务ID对应的任务id不存在："+taskId);
            }
            if (taskDefKey.equals(task.getTaskDefinitionKey())) {
                // 如果删除的是当前节点，就要处理，当前的任务
                if (task.getExecutionId()!=null) {
                    ExecutionEntity executionEntity = CommandContextUtil.getExecutionEntityManager(commandContext).findById(task.getExecutionId());
                    if (flowElement instanceof FlowNode) {
                        ActivityBehavior activityBehavior = (ActivityBehavior) ((UserTask) flowElement).getBehavior();
                        FlowNodeActivityBehavior flowNodeActivityBehavior = (FlowNodeActivityBehavior) activityBehavior;
                        flowNodeActivityBehavior.deleteFlowTask(executionEntity);
                        flowNodeActivityBehavior.leave(executionEntity);
                    }
                }
            }
        } else {
            throw new FlowableIllegalArgumentException("该节点不能删除：");
        }
        return null;
    }
}
