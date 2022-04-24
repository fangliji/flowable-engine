/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.engine.impl.bpmn.behavior;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.util.CollectionUtil;
import org.flowable.engine.DynamicBpmnConstants;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.event.FlowableMultiInstanceActivityCompletedEvent;
import org.flowable.engine.delegate.event.impl.FlowableEventBuilder;
import org.flowable.engine.impl.bpmn.helper.ClassDelegateCollectionHandler;
import org.flowable.engine.impl.bpmn.helper.DelegateExpressionCollectionHandler;
import org.flowable.engine.impl.bpmn.helper.DelegateExpressionUtil;
import org.flowable.engine.impl.bpmn.helper.ErrorPropagation;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.BpmnOverrideContext;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.flowable.engine.impl.delegate.FlowableCollectionHandler;
import org.flowable.engine.impl.delegate.SubProcessActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityManager;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.IdentityLinkUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.identitylink.service.impl.persistence.entity.IdentityLinkEntity;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the multi-instance functionality as described in the BPMN 2.0 spec.
 * 
 * Multi instance functionality is implemented as an {@link ActivityBehavior} that wraps the original {@link ActivityBehavior} of the activity.
 * 
 * Only subclasses of {@link AbstractBpmnActivityBehavior} can have multi-instance behavior. As such, special logic is contained in the {@link AbstractBpmnActivityBehavior} to delegate to the
 * {@link CustomMultiInstanceActivityBehavior} if needed.
 * 
 * @author Joram Barrez
 * @author Tijs Rademakers
 */
public abstract class CustomMultiInstanceActivityBehavior extends FlowNodeActivityBehavior implements SubProcessActivityBehavior {

    private static final long serialVersionUID = 1L;

    protected static final Logger LOGGER = LoggerFactory.getLogger(CustomMultiInstanceActivityBehavior.class);
    protected static final String DELETE_REASON_END = "MI_END";

    // Variable names for outer instance(as described in spec)
    protected final String NUMBER_OF_INSTANCES = "nrOfInstances";
    protected final String NUMBER_OF_ACTIVE_INSTANCES = "nrOfActiveInstances";
    protected final String NUMBER_OF_COMPLETED_INSTANCES = "nrOfCompletedInstances";
    protected final String NUMBER_OF_CANDIDATEUSERS = "nrOfCandidateUsers";
    // Instance members
    protected Activity activity;
    protected AbstractBpmnActivityBehavior innerActivityBehavior;
    protected Expression loopCardinalityExpression;
    protected String completionCondition;
    protected Expression collectionExpression;
    protected String collectionVariable; // Not used anymore. Left here for backwards compatibility.
    protected String collectionElementVariable;
    protected String collectionString;
    protected CollectionHandler collectionHandler;
    // default variable name for loop counter for inner instances (as described in the spec)
    protected String collectionElementIndexVariable = "loopCounter";
    protected String candidateUsersIndex = "loopCandidateUsersIndex";


    /**
     * @param activity
     * @param innerActivityBehavior
     *            The original {@link ActivityBehavior} of the activity that will be wrapped inside this behavior.
     */
    public CustomMultiInstanceActivityBehavior(Activity activity, AbstractBpmnActivityBehavior innerActivityBehavior) {
        this.activity = activity;
        setInnerActivityBehavior(innerActivityBehavior);
    }

    @Override
    public void execute(DelegateExecution delegateExecution) {
        ExecutionEntity execution = (ExecutionEntity) delegateExecution;
        if (getLocalLoopVariable(execution, getCollectionElementIndexVariable()) == null) {

            int nrOfInstances = 0;
            //inited,怀疑这个对象是虚拟化了的，每次拿的是同一个
            setLoopVariable(getMultiInstanceRootExecution(execution),candidateUsersIndex,0);
            try {
                nrOfInstances = createInstances(delegateExecution);
            } catch (BpmnError error) {
                ErrorPropagation.propagateError(error, execution);
            }
            if (nrOfInstances==-1) {
                //TODO:需要走普通任务的走法
                Object nrOfCandidateusers = execution.getVariableLocal(NUMBER_OF_CANDIDATEUSERS);
                DelegateExecution execution1 = clearMulitRootExecution(execution);
                setLoopVariable(execution1, NUMBER_OF_CANDIDATEUSERS,nrOfCandidateusers.toString() );
                innerActivityBehavior.execute(execution1);
            }
            if (nrOfInstances == 0) {
                cleanupMiRoot(execution);
            }

        } else {
            // for synchronous, history was created already in ContinueMultiInstanceOperation,
            // but that would lead to wrong timings for asynchronous which is why it's here
            if (activity.isAsynchronous()) {
                CommandContextUtil.getActivityInstanceEntityManager().recordActivityStart(execution);
            }
            Integer index = getLoopVariable(execution,candidateUsersIndex);
            innerActivityBehavior.multiInstanceExcute(execution,index);
            ++index;
            setLoopVariable(getMultiInstanceRootExecution(execution),candidateUsersIndex,index);

        }
    }

    protected abstract int createInstances(DelegateExecution execution);
    
    @Override
    public void leave(DelegateExecution execution) {
        cleanupMiRoot(execution);
    }

    protected void cleanupMiRoot(DelegateExecution execution) {
        // Delete multi instance root and all child executions.
        // Create a fresh execution to continue
        
        ExecutionEntity multiInstanceRootExecution = (ExecutionEntity) getMultiInstanceRootExecution(execution);
        FlowElement flowElement = multiInstanceRootExecution.getCurrentFlowElement();
        ExecutionEntity parentExecution = multiInstanceRootExecution.getParent();
        
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END);
        executionEntityManager.delete(multiInstanceRootExecution);

        ExecutionEntity newExecution = executionEntityManager.createChildExecution(parentExecution);
        newExecution.setCurrentFlowElement(flowElement);
        super.leave(newExecution);
    }

    protected DelegateExecution clearMulitRootExecution(DelegateExecution execution) {
        // Delete multi instance root and all child executions.
        // Create a fresh execution to continue

        ExecutionEntity multiInstanceRootExecution = (ExecutionEntity) getMultiInstanceRootExecution(execution);
        FlowElement flowElement = multiInstanceRootExecution.getCurrentFlowElement();
        ExecutionEntity parentExecution = multiInstanceRootExecution.getParent();

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END);
        executionEntityManager.delete(multiInstanceRootExecution);

        ExecutionEntity newExecution = executionEntityManager.createChildExecution(parentExecution);
        newExecution.setCurrentFlowElement(flowElement);
        return newExecution;

    }


    protected void executeCompensationBoundaryEvents(FlowElement flowElement, DelegateExecution execution) {

        // Execute compensation boundary events
        Collection<BoundaryEvent> boundaryEvents = findBoundaryEventsForFlowNode(execution.getProcessInstanceId(),execution.getProcessDefinitionId(), flowElement);
        if (CollectionUtil.isNotEmpty(boundaryEvents)) {

            // The parent execution becomes a scope, and a child execution is created for each of the boundary events
            for (BoundaryEvent boundaryEvent : boundaryEvents) {

                if (CollectionUtil.isEmpty(boundaryEvent.getEventDefinitions())) {
                    continue;
                }

                if (boundaryEvent.getEventDefinitions().get(0) instanceof CompensateEventDefinition) {
                    ExecutionEntity childExecutionEntity = CommandContextUtil.getExecutionEntityManager()
                            .createChildExecution((ExecutionEntity) execution);
                    childExecutionEntity.setParentId(execution.getId());
                    childExecutionEntity.setCurrentFlowElement(boundaryEvent);
                    childExecutionEntity.setScope(false);

                    ActivityBehavior boundaryEventBehavior = ((ActivityBehavior) boundaryEvent.getBehavior());
                    boundaryEventBehavior.execute(childExecutionEntity);
                }
            }
        }
    }

    protected Collection<BoundaryEvent> findBoundaryEventsForFlowNode(final String processInstanceId,final String processDefinitionId, final FlowElement flowElement) {
        Process process = getProcessDefinition(processInstanceId,processDefinitionId);

        // This could be cached or could be done at parsing time
        List<BoundaryEvent> results = new ArrayList<>(1);
        Collection<BoundaryEvent> boundaryEvents = process.findFlowElementsOfType(BoundaryEvent.class, true);
        for (BoundaryEvent boundaryEvent : boundaryEvents) {
            if (boundaryEvent.getAttachedToRefId() != null && boundaryEvent.getAttachedToRefId().equals(flowElement.getId())) {
                results.add(boundaryEvent);
            }
        }
        return results;
    }

    protected Process getProcessDefinition(String processInstanceId,String processDefinitionId) {
        return ProcessDefinitionUtil.getProcess(processInstanceId,processDefinitionId);
    }

    // Intercepts signals, and delegates it to the wrapped {@link ActivityBehavior}.
    @Override
    public void trigger(DelegateExecution execution, String signalName, Object signalData) {
        innerActivityBehavior.trigger(execution, signalName, signalData);
    }

    // required for supporting embedded subprocesses
    public void lastExecutionEnded(DelegateExecution execution) {
        // ScopeUtil.createEventScopeExecution((ExecutionEntity) execution);
        leave(execution);
    }

    // required for supporting external subprocesses
    @Override
    public void completing(DelegateExecution execution, DelegateExecution subProcessInstance) throws Exception {
    }

    // required for supporting external subprocesses
    @Override
    public void completed(DelegateExecution execution) throws Exception {
        leave(execution);
    }
    
    public boolean completionConditionSatisfied(DelegateExecution execution) {
        if (completionCondition != null) {
            
            ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration();
            ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
            
            String activeCompletionCondition = null;

            if (CommandContextUtil.getProcessEngineConfiguration().isEnableProcessDefinitionInfoCache()) {
                ObjectNode taskElementProperties = BpmnOverrideContext.getBpmnOverrideElementProperties(activity.getId(), execution.getProcessDefinitionId());
                activeCompletionCondition = getActiveValue(completionCondition, DynamicBpmnConstants.MULTI_INSTANCE_COMPLETION_CONDITION, taskElementProperties);

            } else {
                activeCompletionCondition = completionCondition;
            }
            
            Object value = expressionManager.createExpression(activeCompletionCondition).getValue(execution);
            
            if (!(value instanceof Boolean)) {
                throw new FlowableIllegalArgumentException("completionCondition '" + activeCompletionCondition + "' does not evaluate to a boolean value");
            }

            Boolean booleanValue = (Boolean) value;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Completion condition of multi-instance satisfied: {}", booleanValue);
            }
            return booleanValue;
        }
        return false;
    }
    
    public Integer getLoopVariable(DelegateExecution execution, String variableName) {
        Object value = execution.getVariableLocal(variableName);
        DelegateExecution parent = execution.getParent();
        while (value == null && parent != null) {
            value = parent.getVariableLocal(variableName);
            parent = parent.getParent();
        }
        return (Integer) (value != null ? value : 0);
    }


    // Helpers
    // //////////////////////////////////////////////////////////////////////

    protected void sendCompletedWithConditionEvent(DelegateExecution execution) {
        CommandContextUtil.getEventDispatcher(CommandContextUtil.getCommandContext()).dispatchEvent(
                buildCompletedEvent(execution, FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_COMPLETED_WITH_CONDITION));
    }

    protected void sendCompletedEvent(DelegateExecution execution) {
        CommandContextUtil.getEventDispatcher(CommandContextUtil.getCommandContext()).dispatchEvent(
                buildCompletedEvent(execution, FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_COMPLETED));
    }

    protected FlowableMultiInstanceActivityCompletedEvent buildCompletedEvent(DelegateExecution execution, FlowableEngineEventType eventType) {
        FlowElement flowNode = execution.getCurrentFlowElement();

        return FlowableEventBuilder.createCustomMultiInstanceActivityCompletedEvent(eventType,
                (int) execution.getVariable(NUMBER_OF_INSTANCES),
                (int) execution.getVariable(NUMBER_OF_ACTIVE_INSTANCES),
                (int) execution.getVariable(NUMBER_OF_COMPLETED_INSTANCES),
                flowNode.getId(),
                flowNode.getName(), execution.getId(), execution.getProcessInstanceId(), execution.getProcessDefinitionId(), flowNode);
    }

    @SuppressWarnings("rawtypes")
    protected int resolveNrOfInstances(DelegateExecution execution) {
        // 获取候选人数量，去重后
        int candidateUsersNum = innerActivityBehavior.getCandidateUsersNum(execution);
        // 设置进变量表，避免每次动态拿的人员值不一样，且缓存里面没有，人员也需要缓存，这个还麻烦，
        setLoopVariable(getMultiInstanceRootExecution(execution), NUMBER_OF_CANDIDATEUSERS, candidateUsersNum);
        return candidateUsersNum;

    }

    @SuppressWarnings("rawtypes")
    protected void executeOriginalBehavior(DelegateExecution execution, ExecutionEntity multiInstanceRootExecution, int loopCounter) {
        /*if (usesCollection() && collectionElementVariable != null) {
            Collection collection = (Collection) resolveAndValidateCollection(execution);

            Object value = null;
            int index = 0;
            Iterator it = collection.iterator();
            while (index <= loopCounter) {
                value = it.next();
                index++;
            }
            setLoopVariable(execution, collectionElementVariable, value);
        }*/

        execution.setCurrentFlowElement(activity);
        CommandContextUtil.getAgenda().planContinueMultiInstanceOperation((ExecutionEntity) execution, multiInstanceRootExecution, loopCounter);
    }

    @SuppressWarnings("rawtypes")
    protected Collection resolveAndValidateCollection(DelegateExecution execution) {
        Object obj = resolveCollection(execution);
        if (collectionHandler != null ) {           
            return createFlowableCollectionHandler(collectionHandler, execution).resolveCollection(obj, execution);
        } else {
            if (obj instanceof Collection) {
                return (Collection) obj;
                
            } else if (obj instanceof Iterable) {
                return iterableToCollection((Iterable) obj);
                
            } else if (obj instanceof String) {
                Object collectionVariable = execution.getVariable((String) obj);
                if (collectionVariable instanceof Collection) {
                    return (Collection) collectionVariable;
                } else if (collectionVariable instanceof Iterable) {
                    return iterableToCollection((Iterable) collectionVariable);
                } else if (collectionVariable == null) {
                    throw new FlowableIllegalArgumentException("Variable '" + obj + "' was not found");
                } else {
                    throw new FlowableIllegalArgumentException("Variable '" + obj + "':" + collectionVariable + " is not a Collection");
                }
                
            } else {
                throw new FlowableIllegalArgumentException("Couldn't resolve collection expression, variable reference or string");
                
            }
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Collection iterableToCollection(Iterable iterable) {
        List result = new ArrayList();
        iterable.forEach(element -> result.add(element));
        return result;
    }

    protected Object resolveCollection(DelegateExecution execution) {
        Object collection = null;
        if (collectionExpression != null) {
            collection = collectionExpression.getValue(execution);

        } else if (collectionVariable != null) {
            collection = execution.getVariable(collectionVariable);
            
        } else if (collectionString != null) {
            collection = collectionString;
        }
        return collection;
    }

    protected boolean usesCollection() {
        return collectionExpression != null || collectionVariable != null || collectionString != null;
    }

    protected boolean isExtraScopeNeeded(FlowNode flowNode) {
        return flowNode.getSubProcess() != null;
    }

    protected int resolveLoopCardinality(DelegateExecution execution) {
        // Using Number since expr can evaluate to eg. Long (which is also the default for Juel)
        Object value = loopCardinalityExpression.getValue(execution);
        if (value instanceof Number) {
            return ((Number) value).intValue();

        } else if (value instanceof String) {
            return Integer.valueOf((String) value);

        } else {
            throw new FlowableIllegalArgumentException("Could not resolve loopCardinality expression '" + loopCardinalityExpression.getExpressionText() + "': not a number nor number String");
        }
    }

    protected void setLoopVariable(DelegateExecution execution, String variableName, Object value) {
        execution.setVariableLocal(variableName, value);
    }

    protected Integer getLocalLoopVariable(DelegateExecution execution, String variableName) {
        Map<String, Object> localVariables = execution.getVariablesLocal();
        if (localVariables.containsKey(variableName)) {
            return (Integer) execution.getVariableLocal(variableName);

        } else if (!execution.isMultiInstanceRoot()) {
            DelegateExecution parentExecution = execution.getParent();
            localVariables = parentExecution.getVariablesLocal();
            if (localVariables.containsKey(variableName)) {
                return (Integer) parentExecution.getVariableLocal(variableName);

            } else if (!parentExecution.isMultiInstanceRoot()) {
                DelegateExecution superExecution = parentExecution.getParent();
                return (Integer) superExecution.getVariableLocal(variableName);

            } else {
                return null;
            }

        } else {
            return null;
        }
    }

    protected Object getLocalLoopVariableVal(DelegateExecution execution, String variableName) {
        Map<String, Object> localVariables = execution.getVariablesLocal();
        if (localVariables.containsKey(variableName)) {
            return  execution.getVariableLocal(variableName);

        } else if (!execution.isMultiInstanceRoot()) {
            DelegateExecution parentExecution = execution.getParent();
            localVariables = parentExecution.getVariablesLocal();
            if (localVariables.containsKey(variableName)) {
                return  parentExecution.getVariableLocal(variableName);

            } else if (!parentExecution.isMultiInstanceRoot()) {
                DelegateExecution superExecution = parentExecution.getParent();
                return  superExecution.getVariableLocal(variableName);

            } else {
                return null;
            }

        } else {
            return null;
        }
    }

    /**
     * Since no transitions are followed when leaving the inner activity, it is needed to call the end listeners yourself.
     */
    protected void callActivityEndListeners(DelegateExecution execution) {
        CommandContextUtil.getProcessEngineConfiguration().getListenerNotificationHelper()
                .executeExecutionListeners(activity, execution, ExecutionListener.EVENTNAME_END);
    }

    protected void logLoopDetails(DelegateExecution execution, String custom, int loopCounter, int nrOfCompletedInstances, int nrOfActiveInstances, int nrOfInstances) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Multi-instance '{}' {}. Details: loopCounter={}, nrOrCompletedInstances={},nrOfActiveInstances={},nrOfInstances={}",
                    execution.getCurrentFlowElement() != null ? execution.getCurrentFlowElement().getId() : "", custom, loopCounter,
                    nrOfCompletedInstances, nrOfActiveInstances, nrOfInstances);
        }
    }

    protected DelegateExecution getMultiInstanceRootExecution(DelegateExecution executionEntity) {
        DelegateExecution multiInstanceRootExecution = null;
        DelegateExecution currentExecution = executionEntity;
        while (currentExecution != null && multiInstanceRootExecution == null && currentExecution.getParent() != null) {
            if (currentExecution.isMultiInstanceRoot()) {
                multiInstanceRootExecution = currentExecution;
            } else {
                currentExecution = currentExecution.getParent();
            }
        }
        return multiInstanceRootExecution;
    }
    
    protected String getActiveValue(String originalValue, String propertyName, ObjectNode taskElementProperties) {
        String activeValue = originalValue;
        if (taskElementProperties != null) {
            JsonNode overrideValueNode = taskElementProperties.get(propertyName);
            if (overrideValueNode != null) {
                if (overrideValueNode.isNull()) {
                    activeValue = null;
                } else {
                    activeValue = overrideValueNode.asText();
                }
            }
        }
        return activeValue;
    }

    protected FlowableCollectionHandler createFlowableCollectionHandler(CollectionHandler handler, DelegateExecution execution) {
    	FlowableCollectionHandler collectionHandler = null;

        if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equalsIgnoreCase(handler.getImplementationType())) {
        	collectionHandler = new ClassDelegateCollectionHandler(handler.getImplementation(), null);
        
        } else if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equalsIgnoreCase(handler.getImplementationType())) {
        	Object delegate = DelegateExpressionUtil.resolveDelegateExpression(CommandContextUtil.getProcessEngineConfiguration().getExpressionManager().createExpression(handler.getImplementation()), execution);
            if (delegate instanceof FlowableCollectionHandler) {
                collectionHandler = new DelegateExpressionCollectionHandler(execution, CommandContextUtil.getProcessEngineConfiguration().getExpressionManager().createExpression(handler.getImplementation()));   
            } else {
                throw new FlowableIllegalArgumentException("Delegate expression " + handler.getImplementation() + " did not resolve to an implementation of " + FlowableCollectionHandler.class);
            }
        }
        return collectionHandler;
    }

    /**
     * 动态减签，并行加任务，并行减节点
     * @param execution
     * @param candidateUsers
     * @return
     */
    protected boolean dynamicSubSignature (DelegateExecution execution,String candidateUsers) {
        // step 1 获取多实例的执行根，从根上获取对应的变量，设置变量
        DelegateExecution multiRoot = getMultiInstanceRootExecution (execution);
        List<String> addCandidateUsers = analysisDynamicSubCandidateUser(multiRoot,candidateUsers);
        // step 2 ,如果顺序签，则要判断当前的顺序在那个位置，现在只剩下一个人了
        dealSubSignatureExecution(multiRoot,addCandidateUsers);
        return false;

    }

    /**
     * 生成加的执行链和任务，修改当前的缓存的人员数据，todo,修改流程定义的状态改节点编辑过，
     * @param multiRoot
     * @Param candidateUsers
     */
    protected void dealSubSignatureExecution (DelegateExecution multiRoot, List<String> candidateUsers) {
        // nothing  留给子类


    }



    private List<String> analysisDynamicSubCandidateUser(DelegateExecution execution,String candidateUsers) {
        List<String> candidateUsersList = this.innerActivityBehavior.getCacheCandidateUsers(execution);
        List<String> candidates = getDynamicCandidateUser(execution,candidateUsers);
        if (candidates!=null) {
            if (!candidateUsersList.containsAll(candidates)) {
                throw new FlowableIllegalArgumentException("减签人员在当前审批人员中！candidateUsers:"+StringUtils.join(candidateUsersList,","));
            }
            candidateUsersList.removeAll(candidates);
            if (candidateUsersList.isEmpty()) {
                throw new FlowableIllegalArgumentException("减签至少保留一个人员！candidateUsers:"+StringUtils.join(candidateUsersList,","));
            }
        }
        setLoopVariable(execution,AbstractBpmnActivityBehavior.MULTIINSTANCECACHEUSERS,StringUtils.join(candidateUsersList,","));
        return candidateUsersList;
    }

    /**
     * 动态加签 分为两种，运行时多实例，加任务，也阔以加节点
     * @return
     */
    protected boolean dynamicAddSignature (DelegateExecution execution,String candidateUsers) {
        // step 1 获取 多实例的执行根，从跟上获取对应的变量，设置变量
        DelegateExecution multiRoot = getMultiInstanceRootExecution (execution);
        // step 2 解析 人员数量，修改变量中的缓存人员数量
        List<String> addCandidateUsers = analysisDynamicAddCandidateUser(multiRoot,candidateUsers);
        if (addCandidateUsers == null) {
            throw new FlowableIllegalArgumentException("加签人员不能为空！candidateUsers:"+candidateUsers);
        }
        // step 3 重新设置实例总数变量，活跃变量数
        int nrOfInstances = getLoopVariable(execution, NUMBER_OF_INSTANCES)+ addCandidateUsers.size();
        int nrOfCompletedInstances = getLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES) ;
        int nrOfActiveInstances = getLoopVariable(execution, NUMBER_OF_ACTIVE_INSTANCES) + addCandidateUsers.size();
        if (multiRoot != null) { // will be null in case of empty collection
            setLoopVariable(multiRoot, NUMBER_OF_COMPLETED_INSTANCES, nrOfCompletedInstances);
            setLoopVariable(multiRoot, NUMBER_OF_ACTIVE_INSTANCES, nrOfActiveInstances);
            setLoopVariable(multiRoot, NUMBER_OF_INSTANCES,nrOfInstances);
        }
        // step 4 lockParentScope
        ExecutionEntity executionEntity = (ExecutionEntity) execution;
        if (executionEntity.getParent() != null) {
            executionEntity.inactivate();
            lockFirstParentScope(executionEntity);
            // step 5 生成任务 和子执行链
            dealAddSignatureExecution (multiRoot,addCandidateUsers);
        }
        return false;
    }

    /**
     * 生成加的执行链和任务，修改当前的缓存的人员数据，todo,修改流程定义的状态改节点编辑过，
     * @param multiRoot
     * @Param candidateUsers
     */
    protected void dealAddSignatureExecution (DelegateExecution multiRoot, List<String> candidateUsers) {
      // nothing  留给子类



    }


    protected void lockFirstParentScope(DelegateExecution execution) {

        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();

        boolean found = false;
        ExecutionEntity parentScopeExecution = null;
        ExecutionEntity currentExecution = (ExecutionEntity) execution;
        while (!found && currentExecution != null && currentExecution.getParentId() != null) {
            parentScopeExecution = executionEntityManager.findById(currentExecution.getParentId());
            if (parentScopeExecution != null && parentScopeExecution.isScope()) {
                found = true;
            }
            currentExecution = parentScopeExecution;
        }

        parentScopeExecution.forceUpdate();
    }


    private List<String> analysisDynamicAddCandidateUser(DelegateExecution execution, String candidateUsers) {
        List<String> candidateUsersList = this.innerActivityBehavior.getCacheCandidateUsers(execution);
        List<String> candidates = getDynamicCandidateUser(execution,candidateUsers);
        if (candidates!=null) {
            candidateUsersList.addAll(candidates);
        }
        setLoopVariable(execution,AbstractBpmnActivityBehavior.MULTIINSTANCECACHEUSERS,StringUtils.join(candidateUsersList,","));
        return candidates;
    }

    private List<String> getDynamicCandidateUser(DelegateExecution execution, String candidateUsers) {
        CommandContext commandContext = CommandContextUtil.getCommandContext();
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
        Expression userIdExpr = expressionManager.createExpression(candidateUsers);
        Object value = userIdExpr.getValue(execution);
        if (value != null) {
            List<String> candidates = null;
            if (value instanceof Collection) {
                Collection collection = (Collection)value;
                List<Object> list = Collections.EMPTY_LIST;
                list.addAll(collection);
                candidates = list.stream().distinct().collect(Collectors.mapping(o->o.toString(),Collectors.toList()));
            } else {
                String strValue = value.toString();
                if (StringUtils.isNotEmpty(strValue)) {
                    candidates = this.innerActivityBehavior.extractCandidates(strValue);
                    candidates = candidates.stream().distinct().collect(Collectors.toList());
                }
            }
            return candidates;
        }
        return null;
    }

    // Getters and Setters
    // ///////////////////////////////////////////////////////////

    public Expression getLoopCardinalityExpression() {
        return loopCardinalityExpression;
    }

    public void setLoopCardinalityExpression(Expression loopCardinalityExpression) {
        this.loopCardinalityExpression = loopCardinalityExpression;
    }

    public String getNumberOfCandidateusers(DelegateExecution execution){
        Object object = execution.getVariable(NUMBER_OF_CANDIDATEUSERS);
        return  object == null?null:object.toString();
    }

    public String getCompletionCondition() {
        return completionCondition;
    }


    public void setCompletionCondition(String completionCondition) {
        this.completionCondition = completionCondition;
    }

    public Expression getCollectionExpression() {
        return collectionExpression;
    }

    public void setCollectionExpression(Expression collectionExpression) {
        this.collectionExpression = collectionExpression;
    }

    public String getCollectionVariable() {
        return collectionVariable;
    }

    public void setCollectionVariable(String collectionVariable) {
        this.collectionVariable = collectionVariable;
    }

    public String getCollectionElementVariable() {
        return collectionElementVariable;
    }

    public void setCollectionElementVariable(String collectionElementVariable) {
        this.collectionElementVariable = collectionElementVariable;
    }

    public String getCollectionString() {
        return collectionString;
    }

    public void setCollectionString(String collectionString) {
        this.collectionString = collectionString;
    }

	public CollectionHandler getHandler() {
		return collectionHandler;
	}

	public void setHandler(CollectionHandler collectionHandler) {
		this.collectionHandler = collectionHandler;
	}

	public String getCollectionElementIndexVariable() {
        return collectionElementIndexVariable;
    }

    public void setCollectionElementIndexVariable(String collectionElementIndexVariable) {
        this.collectionElementIndexVariable = collectionElementIndexVariable;
    }

    public void setInnerActivityBehavior(AbstractBpmnActivityBehavior innerActivityBehavior) {
        this.innerActivityBehavior = innerActivityBehavior;
        this.innerActivityBehavior.setCustomMultiInstanceActivityBehavior(this);
    }

    public AbstractBpmnActivityBehavior getInnerActivityBehavior() {
        return innerActivityBehavior;
    }
}
