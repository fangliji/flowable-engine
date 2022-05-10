package org.flowable.engine.impl.cmd;

import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.bpmn.behavior.FlowNodeActivityBehavior;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.dynamic.DynamicAddUserTaskBuilder;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceId,processDefinitionId,true);
        Process process = bpmnModel.getMainProcess();
        FlowElement flowElement = process.getFlowElement(dynamicAddUserTaskBuilder.getTaskKey());
        UserTask userTask = generateUserTask(dynamicAddUserTaskBuilder.nextTaskId(process.getFlowElementMap()), dynamicAddUserTaskBuilder.getName(), dynamicAddUserTaskBuilder.getCandidateUsers());
        process.addFlowElement(userTask);
        FlowNode oldNode = (FlowNode)flowElement;
        // 在节点之前加签，如果加签的节点是正进行中的节点，那么加签之后，就应该跳到加签的节点上处理
        if ("before".equals(dynamicAddUserTaskBuilder.getAddWay())) {
            List<SequenceFlow> incomingFlows = oldNode.getIncomingFlows();
            addUserTask(process,oldNode,userTask,incomingFlows,oldNode);
        } else {
            // 在节点之后加签
            List<SequenceFlow> outgoingFlows = oldNode.getOutgoingFlows();
            SequenceFlow sequenceFlow = outgoingFlows.get(0);
            addUserTask(process,oldNode,userTask,outgoingFlows,(FlowNode) sequenceFlow.getTargetFlowElement());
        }
        try {
            ProcessDefinitionUtil.updateProcess(processInstanceId, bpmnModel);
        } catch (Exception e) {
            throw new FlowableIllegalArgumentException("AddUserTaskCmd"+e.getCause().toString());
        }
        // 更新完成之后，判断是否需要处理节点
        dealJumpToTargetElement(commandContext, flowElement, userTask);
        return null;
    }

    private void addUserTask (Process process,FlowNode oldNode,UserTask addUserTask ,List<SequenceFlow> sequenceFlows ,FlowNode targetFlowNode) {

        sequenceFlows.forEach(sequence -> {
            sequence.setTargetFlowElement(addUserTask);
            sequence.setTargetRef(addUserTask.getId());
        });
        addUserTask.setIncomingFlows(sequenceFlows);
        joinFlowNode(process, addUserTask, Arrays.asList(targetFlowNode),sequenceFlows);

    }

    private void dealJumpToTargetElement(CommandContext commandContext, FlowElement flowElement, UserTask userTask) {
        if ("before".equals(dynamicAddUserTaskBuilder.getAddWay())) {
            List<TaskEntity> taskEntities = CommandContextUtil.getTaskService(commandContext).findTasksByProcessInstanceId(processInstanceId);
            String excutionId = null;
            String taskId = null;
            TaskEntity currentTaskEntity = null;
            for (TaskEntity taskEntity:taskEntities) {
                if (dynamicAddUserTaskBuilder.getTaskKey().equals(taskEntity.getTaskDefinitionKey())) {
                    taskId = taskEntity.getId();
                    excutionId = taskEntity.getExecutionId();
                    break;
                }
            }
            if (excutionId!=null) {
                ExecutionEntity executionEntity = CommandContextUtil.getExecutionEntityManager(commandContext).findById(excutionId);
                if (flowElement instanceof FlowNode) {
                    ActivityBehavior activityBehavior = (ActivityBehavior) ((UserTask) flowElement).getBehavior();
                    FlowNodeActivityBehavior flowNodeActivityBehavior = (FlowNodeActivityBehavior) activityBehavior;
                    flowNodeActivityBehavior.jumpToTargetFlowElement(executionEntity,userTask);
                }
            }
            // 调到对应节点的逻辑，是先应该执行leave里面处理excution的逻辑，如果没有，则把 excution 设置当前需要跳转的节点，然后执行流程继续
            // 该类行为，应该封装在父类 flow里面
        }
    }

    private void joinFlowNode(org.flowable.bpmn.model.Process process, FlowNode sourceNode, List<FlowNode> targetNodeList,List<SequenceFlow> oldSequenceFlows) {

        List<SequenceFlow> sequenceFlows = new ArrayList<>();
        for (FlowNode targetNode : targetNodeList) {
            SequenceFlow sequenceFlow = generateSequenceFlow(dynamicAddUserTaskBuilder.nextFlowId(process.getFlowElementMap()));
            process.addFlowElement(sequenceFlow);
            sequenceFlow.setSourceFlowElement(sourceNode);
            sequenceFlow.setSourceRef(sourceNode.getId());
            sequenceFlow.setTargetFlowElement(targetNode);
            sequenceFlow.setTargetRef(targetNode.getId());
            sequenceFlows.add(sequenceFlow);
            addIncomingFlows(targetNode, Arrays.asList(sequenceFlow), oldSequenceFlows);
        }
        addOutgoingFlows(sourceNode, sequenceFlows);

    }
    private void addIncomingFlows(FlowNode flowNode, List<SequenceFlow> sequenceFlows,List<SequenceFlow> oldSequenceFlows) {
        List<SequenceFlow> incomingFlows = flowNode.getIncomingFlows();
        if (incomingFlows == null) {
            incomingFlows = new ArrayList<>();
        } else {
            Set<String> sets = oldSequenceFlows.stream().collect(Collectors.mapping(a->a.getId(),Collectors.toSet()));
            incomingFlows =  incomingFlows.stream().filter(sequenceFlow -> !sets.contains(sequenceFlow.getId())).collect(Collectors.toList());
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

    public  UserTask generateUserTask(String id, String name, List<String> candidates) {
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
    public  Object createUserTaskBehavior(UserTask userTask) {
        UserTaskActivityBehavior userTaskActivityBehavior = new UserTaskActivityBehavior(userTask);
        return userTaskActivityBehavior;
    }


}
