package org.flowable.engine.impl.cmd;

import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.dynamic.DynamicAddUserTaskBuilder;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 类名称：AddUserTaskCmd
 * 类描述：TODO
 * 创建时间：4/14/22 7:49 PM
 * 创建人：flj
 */
public class AddUserTaskCmd implements Command<Void> {
    protected String processInstanceId;
    protected String processDefinitionId;
    protected DynamicAddUserTaskBuilder dynamicAddUserTaskBuilder;

    public AddUserTaskCmd(String processInstanceId ,String processDefinitionId ,DynamicAddUserTaskBuilder dynamicAddUserTaskBuilder) {
        this.dynamicAddUserTaskBuilder = dynamicAddUserTaskBuilder;
        this.processInstanceId = processInstanceId;
        this.processDefinitionId = processDefinitionId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(dynamicAddUserTaskBuilder.getTaskKey());
        UserTask userTask = generateUserTask(dynamicAddUserTaskBuilder.nextTaskId(process.getFlowElementMap()), dynamicAddUserTaskBuilder.getName(), dynamicAddUserTaskBuilder.getCandidateUsers());
        process.addFlowElement(userTask);
        FlowNode oldNode = (FlowNode)flowElement;
        if ("before".equals(dynamicAddUserTaskBuilder.getAddWay())) {
            List<SequenceFlow> incomingFlows = oldNode.getIncomingFlows();
            incomingFlows.forEach(sequenceFlow -> {
                sequenceFlow.setTargetFlowElement(userTask);
                sequenceFlow.setTargetRef(userTask.getId());
            });
            joinFlowNode(process, userTask, Arrays.asList(oldNode));
        } else {
            List<SequenceFlow> outgoingFlows = oldNode.getOutgoingFlows();
            SequenceFlow sequenceFlow = outgoingFlows.get(0);
            outgoingFlows.forEach(sequence -> {
                sequence.setTargetFlowElement(userTask);
                sequence.setTargetRef(userTask.getId());
            });
            joinFlowNode(process, userTask, Arrays.asList((FlowNode) sequenceFlow.getTargetFlowElement()));
        }
        List<TaskEntity> taskEntities = CommandContextUtil.getTaskService(commandContext).findTasksByProcessInstanceId(processInstanceId);
        for (TaskEntity taskEntity:taskEntities) {
            taskEntity.getTaskDefinitionKey();
        }
       /* taskEntities.forEach(taskEntity -> {
           if
        });*/
        // 判断是否当前任务节点加签

        // toDo会签，顺序签，暂时不支持，平行加签
        // deal 动态加签
        return null;
    }

    private void joinFlowNode(org.flowable.bpmn.model.Process process, FlowNode sourceNode, List<FlowNode> targetNodeList) {

        List<SequenceFlow> sequenceFlows = new ArrayList<>();
        for (FlowNode targetNode : targetNodeList) {
            SequenceFlow sequenceFlow = generateSequenceFlow(dynamicAddUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
            process.addFlowElement(sequenceFlow);
            sequenceFlow.setSourceFlowElement(sourceNode);
            sequenceFlow.setSourceRef(sourceNode.getId());
            sequenceFlow.setTargetFlowElement(targetNode);
            sequenceFlow.setTargetRef(targetNode.getId());
            sequenceFlows.add(sequenceFlow);
            addIncomingFlows(targetNode, Arrays.asList(sequenceFlow));
        }
        addOutgoingFlows(sourceNode, sequenceFlows);

    }
    private void addIncomingFlows(FlowNode flowNode, List<SequenceFlow> sequenceFlows) {
        List<SequenceFlow> incomingFlows = flowNode.getIncomingFlows();
        if (incomingFlows == null) {
            incomingFlows = new ArrayList<>();
        }
        incomingFlows.addAll(sequenceFlows);
        flowNode.setIncomingFlows(incomingFlows);
    }

    private void addOutgoingFlows(FlowNode flowNode, List<SequenceFlow> sequenceFlows) {
        List<SequenceFlow> outgoingFlows = flowNode.getOutgoingFlows();
        if (outgoingFlows == null) {
            outgoingFlows = new ArrayList<>();
        }
        outgoingFlows.addAll(sequenceFlows);
        flowNode.setOutgoingFlows(outgoingFlows);
    }

    public  SequenceFlow generateSequenceFlow(String id) {
        SequenceFlow sequenceFlow = new SequenceFlow();
        sequenceFlow.setId(id);
        return sequenceFlow;
    }

    public static UserTask generateUserTask(String id, String name, List<String> candidates) {
        UserTask userTask = new UserTask();
        userTask.setPriority(String.valueOf("6"));
        userTask.setId(id);
        userTask.setCandidateUsers(candidates);
        userTask.setName(name);
        userTask.setBehavior(createUserTaskBehavior(userTask));
        return userTask;
    }

    /**
     * 生成任务节点行为类
     *
     * @param userTask
     * @return
     */
    public static Object createUserTaskBehavior(UserTask userTask) {
        UserTaskActivityBehavior userTaskActivityBehavior = new UserTaskActivityBehavior(userTask);
        return userTaskActivityBehavior;
    }


}
