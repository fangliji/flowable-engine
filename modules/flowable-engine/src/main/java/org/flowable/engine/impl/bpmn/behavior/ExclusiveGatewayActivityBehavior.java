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

import java.util.Iterator;

import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.event.impl.FlowableEventBuilder;
import org.flowable.engine.impl.bpmn.helper.SkipExpressionUtil;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.condition.ConditionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Exclusive Gateway/XOR gateway/exclusive data-based gateway as defined in the BPMN specification.
 * 
 * @author Joram Barrez
 */
public class ExclusiveGatewayActivityBehavior extends GatewayActivityBehavior {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveGatewayActivityBehavior.class);

    /**
     * The default behaviour of BPMN, taking every outgoing sequence flow (where the condition evaluates to true), is not valid for an exclusive gateway.
     * 
     * Hence, this behaviour is overridden and replaced by the correct behavior: selecting the first sequence flow which condition evaluates to true (or which hasn't got a condition) and leaving the
     * activity through that sequence flow.
     * 
     * If no sequence flow is selected (ie all conditions evaluate to false), then the default sequence flow is taken (if defined).
     */
    @Override
    public void leave(DelegateExecution execution) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Leaving exclusive gateway '{}'", execution.getCurrentActivityId());
        }

        ExclusiveGateway exclusiveGateway = (ExclusiveGateway) execution.getCurrentFlowElement();

        if (CommandContextUtil.getProcessEngineConfiguration() != null && CommandContextUtil.getProcessEngineConfiguration().getEventDispatcher().isEnabled()) {
            CommandContextUtil.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(
                    FlowableEventBuilder.createActivityEvent(FlowableEngineEventType.ACTIVITY_COMPLETED, exclusiveGateway.getId(), exclusiveGateway.getName(), execution.getId(),
                            execution.getProcessInstanceId(), execution.getProcessDefinitionId(), exclusiveGateway));
        }

        SequenceFlow outgoingSequenceFlow = null;
        SequenceFlow defaultSequenceFlow = null;
        String defaultSequenceFlowId = exclusiveGateway.getDefaultFlow();
        // 第一步先判断链接线上面是否有优先级，如果没有优先级，则不说了，如果有优先级，按优先级排序
        boolean flag = true;
        // 直接找出有优先级且优先级是最高的
        SequenceFlow highestPriority = null;
        String initPriority = null;
        // Determine sequence flow to take
        Iterator<SequenceFlow> sequenceFlowIterator = exclusiveGateway.getOutgoingFlows().iterator();
        while (outgoingSequenceFlow == null && sequenceFlowIterator.hasNext()) {
            SequenceFlow sequenceFlow = sequenceFlowIterator.next();

            String skipExpressionString = sequenceFlow.getSkipExpression();
            if (!SkipExpressionUtil.isSkipExpressionEnabled(execution, skipExpressionString)) {
                boolean conditionEvaluatesToTrue = ConditionUtil.hasTrueCondition(sequenceFlow, execution);
                if (conditionEvaluatesToTrue && (defaultSequenceFlowId == null || !defaultSequenceFlowId.equals(sequenceFlow.getId()))) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Sequence flow '{}' selected as outgoing sequence flow.", sequenceFlow.getId());
                    }
                    // 获取条件优先级
                    String priority = sequenceFlow.getConditionPriority();
                    if (priority==null || "".equals(priority)) {
                        outgoingSequenceFlow = sequenceFlow;
                        flag = false;
                        break;
                    }
                    if (initPriority ==null) {
                        initPriority = priority;
                    }
                    if (initPriority.compareTo(priority)>=0) {
                        initPriority = priority;
                        highestPriority = sequenceFlow;
                    }
                }
            } else if (SkipExpressionUtil.shouldSkipFlowElement(Context.getCommandContext(), execution, skipExpressionString)) {
                outgoingSequenceFlow = sequenceFlow;
            }

            // Already store it, if we would need it later. Saves one for loop.
            if (defaultSequenceFlowId != null && defaultSequenceFlowId.equals(sequenceFlow.getId())) {
                defaultSequenceFlow = sequenceFlow;
            }

        }
        if (flag &&  highestPriority!=null ) {
            // 标记为true，说明有优先级，
            outgoingSequenceFlow = highestPriority;
        }
        // Leave the gateway
        if (outgoingSequenceFlow != null) {
            execution.setCurrentFlowElement(outgoingSequenceFlow);
        } else {
            if (defaultSequenceFlow != null) {
                execution.setCurrentFlowElement(defaultSequenceFlow);
            } else {

                // No sequence flow could be found, not even a default one
                throw new FlowableException("No outgoing sequence flow of the exclusive gateway '" + exclusiveGateway.getId() + "' could be selected for continuing the process");
            }
        }

        super.leave(execution);
    }
}
