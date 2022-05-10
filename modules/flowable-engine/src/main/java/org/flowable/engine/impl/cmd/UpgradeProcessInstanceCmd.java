package org.flowable.engine.impl.cmd;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.impl.dynamic.DynamicUpgradeProcessInstanceBuilder;
import org.flowable.engine.impl.persistence.entity.*;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.engine.repository.ProcessDefinition;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 类名称：UpgradeProcessInstanceCmd
 * 类描述：TODO
 * 创建时间：4/29/22 2:16 PM
 * 创建人：flj
 */
public class UpgradeProcessInstanceCmd implements Command<String>, Serializable {
    protected DynamicUpgradeProcessInstanceBuilder dynamicUpgradeProcessInstanceBuilder;

    public DynamicUpgradeProcessInstanceBuilder getDynamicUpgradeProcessInstanceBuilder() {
        return dynamicUpgradeProcessInstanceBuilder;
    }

    public void setDynamicUpgradeProcessInstanceBuilder(DynamicUpgradeProcessInstanceBuilder dynamicUpgradeProcessInstanceBuilder) {
        this.dynamicUpgradeProcessInstanceBuilder = dynamicUpgradeProcessInstanceBuilder;
    }

    @Override
    public String execute(CommandContext commandContext) {
        String processDefinitionId = dynamicUpgradeProcessInstanceBuilder.getProcessDefinitionId();
        String processInstanceId = dynamicUpgradeProcessInstanceBuilder.getProcessInstanceId();
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId,true);
        Process process = bpmnModel.getMainProcess();
        // 这个时候寻找最新的流程定义
        ProcessDefinitionEntityManager processDefinitionEntityManager = CommandContextUtil.getProcessDefinitionEntityManager();
        ProcessDefinition oldDefinition = processDefinitionEntityManager.findById(processDefinitionId);
        ProcessDefinition nowDefinition = processDefinitionEntityManager.findLatestProcessDefinitionByKeyAndTenantId(oldDefinition.getKey(),oldDefinition.getTenantId());
        if (!oldDefinition.getId().equals(nowDefinition.getId())) {
            BpmnModel newBpmnModel = ProcessDefinitionUtil.getBpmnModel(null,nowDefinition.getId(),true);
            Process nowProcess = newBpmnModel.getMainProcess();
            // compareNowProcess
            Collection<FlowElement> flowElements = process.getFlowElements();
            flowElements.stream().forEach(flowElement -> {
               FlowElement flowElementNew = nowProcess.getFlowElement(flowElement.getId());
               if (flowElementNew!=null && isUseOldFlowElement(flowElement)) {
                    flowElementNew.copyOther(flowElement);
               }
            });
            try{
                ProcessDefinitionUtil.updateProcess(processInstanceId,newBpmnModel);
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("流程升级异常："+e.getCause().toString());
            }
            // DEAL Eexcution; TODO:: 处理Excution ,
            ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager(commandContext);
            Collection<ExecutionEntity> executionEntityList = executionEntityManager.findChildExecutionsByParentExecutionId(processInstanceId);
            ExecutionEntity processInstance = executionEntityManager.findByRootProcessInstanceId(processInstanceId);
            Set<String> activityIds = new HashSet<>();
            HistoricProcessInstanceEntityManager historicProcessInstanceManager = CommandContextUtil.getHistoricProcessInstanceEntityManager(commandContext);
            HistoricProcessInstance historicProcessInstance = historicProcessInstanceManager.findHistoricProcessInstancesByProcessInstanceId(processInstanceId);
            //todo::
            HistoricProcessInstanceEntityImpl entity = (HistoricProcessInstanceEntityImpl)historicProcessInstance;
            entity.setProcessDefinitionId(nowDefinition.getId());
            entity.setProcessDefinitionKey(nowDefinition.getKey());
            entity.setProcessDefinitionName(nowDefinition.getName());
            entity.setProcessDefinitionVersion(nowDefinition.getVersion());
            entity.setDeploymentId(nowDefinition.getDeploymentId());
            historicProcessInstanceManager.update(entity);
            // 更新新节点
            processInstance.setProcessDefinitionId(nowDefinition.getId());
            processInstance.setProcessDefinitionKey(nowDefinition.getKey());
            processInstance.setProcessDefinitionName(nowDefinition.getName());
            processInstance.setProcessDefinitionVersion(nowDefinition.getVersion());
            processInstance.setDeploymentId(nowDefinition.getDeploymentId());
            executionEntityManager.update(processInstance);
            executionEntityList.forEach(executionEntity -> {
                //TODO::排查发起节点
                activityIds.add(executionEntity.getActivityId());
                executionEntityManager.deleteExecutionAndRelatedData(executionEntity, executionEntity.getDeleteReason(), false, true, null);
                executionEntityManager.delete(executionEntity);
                // 删除Eexcution;
                // 删除Task;
            });
            ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext).createChildExecution(processInstance);
            execution.setCurrentFlowElement(nowProcess.getInitialFlowElement());
               // Delete all child executions
            CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(execution);
            return execution.getId();
            // 在外层来调用
        }

        return null;
    }

    private boolean isUseOldFlowElement(FlowElement flowElement) {
        if ((flowElement instanceof UserTask) && "1".equals(flowElement.getEditFlag())) {
            return true;
        }
        return false;
    }
}
