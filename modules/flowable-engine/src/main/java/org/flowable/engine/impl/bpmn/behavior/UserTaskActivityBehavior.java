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

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.calendar.BusinessCalendar;
import org.flowable.common.engine.impl.calendar.DueDateBusinessCalendar;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.DynamicBpmnConstants;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.bpmn.helper.SkipExpressionUtil;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.BpmnOverrideContext;
import org.flowable.engine.impl.context.Context;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.EntityLinkUtil;
import org.flowable.engine.impl.util.IdentityLinkUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.identitylink.service.impl.persistence.entity.IdentityLinkEntity;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.event.impl.FlowableTaskEventBuilder;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flowable.engine.impl.bpmn.data.ApproverNoStrategyProperties;

/**
 * @author Joram Barrez
 */
public class UserTaskActivityBehavior extends TaskActivityBehavior {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskActivityBehavior.class);

    protected UserTask userTask;

    public UserTaskActivityBehavior(UserTask userTask) {
        this.userTask = userTask;
    }

    @Override
    public void execute(DelegateExecution execution) {
        CommandContext commandContext = CommandContextUtil.getCommandContext();
        TaskService taskService = CommandContextUtil.getTaskService(commandContext);

        TaskEntity task = taskService.createTask();
        task.setExecutionId(execution.getId());
        task.setTaskDefinitionKey(userTask.getId());

        String activeTaskName = null;
        String activeTaskDescription = null;
        String activeTaskDueDate = null;
        String activeTaskPriority = null;
        String activeTaskCategory = null;
        String activeTaskFormKey = null;
        String activeTaskSkipExpression = null;
        String activeTaskAssignee = null;
        String activeTaskOwner = null;
        List<String> activeTaskCandidateUsers = null;
        List<String> activeTaskCandidateGroups = null;

        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();

        if (CommandContextUtil.getProcessEngineConfiguration(commandContext).isEnableProcessDefinitionInfoCache()) {
            ObjectNode taskElementProperties = BpmnOverrideContext.getBpmnOverrideElementProperties(userTask.getId(), execution.getProcessDefinitionId());
            activeTaskName = getActiveValue(userTask.getName(), DynamicBpmnConstants.USER_TASK_NAME, taskElementProperties);
            activeTaskDescription = getActiveValue(userTask.getDocumentation(), DynamicBpmnConstants.USER_TASK_DESCRIPTION, taskElementProperties);
            activeTaskDueDate = getActiveValue(userTask.getDueDate(), DynamicBpmnConstants.USER_TASK_DUEDATE, taskElementProperties);
            activeTaskPriority = getActiveValue(userTask.getPriority(), DynamicBpmnConstants.USER_TASK_PRIORITY, taskElementProperties);
            activeTaskCategory = getActiveValue(userTask.getCategory(), DynamicBpmnConstants.USER_TASK_CATEGORY, taskElementProperties);
            activeTaskFormKey = getActiveValue(userTask.getFormKey(), DynamicBpmnConstants.USER_TASK_FORM_KEY, taskElementProperties);
            activeTaskSkipExpression = getActiveValue(userTask.getSkipExpression(), DynamicBpmnConstants.TASK_SKIP_EXPRESSION, taskElementProperties);
            activeTaskAssignee = getActiveValue(userTask.getAssignee(), DynamicBpmnConstants.USER_TASK_ASSIGNEE, taskElementProperties);
            activeTaskOwner = getActiveValue(userTask.getOwner(), DynamicBpmnConstants.USER_TASK_OWNER, taskElementProperties);
            activeTaskCandidateUsers = getActiveValueList(userTask.getCandidateUsers(), DynamicBpmnConstants.USER_TASK_CANDIDATE_USERS, taskElementProperties);
            activeTaskCandidateGroups = getActiveValueList(userTask.getCandidateGroups(), DynamicBpmnConstants.USER_TASK_CANDIDATE_GROUPS, taskElementProperties);

        } else {
            activeTaskName = userTask.getName();
            activeTaskDescription = userTask.getDocumentation();
            activeTaskDueDate = userTask.getDueDate();
            activeTaskPriority = userTask.getPriority();
            activeTaskCategory = userTask.getCategory();
            activeTaskFormKey = userTask.getFormKey();
            activeTaskSkipExpression = userTask.getSkipExpression();
            activeTaskAssignee = userTask.getAssignee();
            activeTaskOwner = userTask.getOwner();
            activeTaskCandidateUsers = userTask.getCandidateUsers();
            activeTaskCandidateGroups = userTask.getCandidateGroups();
        }

        if (StringUtils.isNotEmpty(activeTaskName)) {
            String name = null;
            try {
                Object nameValue = expressionManager.createExpression(activeTaskName).getValue(execution);
                if (nameValue != null) {
                    name = nameValue.toString();
                }
            } catch (FlowableException e) {
                name = activeTaskName;
                LOGGER.warn("property not found in task name expression {}", e.getMessage());
            }
            task.setName(name);
        }

        if (StringUtils.isNotEmpty(activeTaskDescription)) {
            String description = null;
            try {
                Object descriptionValue = expressionManager.createExpression(activeTaskDescription).getValue(execution);
                if (descriptionValue != null) {
                    description = descriptionValue.toString();
                }
            } catch (FlowableException e) {
                description = activeTaskDescription;
                LOGGER.warn("property not found in task description expression {}", e.getMessage());
            }
            task.setDescription(description);
        }

        if (StringUtils.isNotEmpty(activeTaskDueDate)) {
            Object dueDate = expressionManager.createExpression(activeTaskDueDate).getValue(execution);
            if (dueDate != null) {
                if (dueDate instanceof Date) {
                    task.setDueDate((Date) dueDate);
                } else if (dueDate instanceof String) {
                    String businessCalendarName = null;
                    if (StringUtils.isNotEmpty(userTask.getBusinessCalendarName())) {
                        businessCalendarName = expressionManager.createExpression(userTask.getBusinessCalendarName()).getValue(execution).toString();
                    } else {
                        businessCalendarName = DueDateBusinessCalendar.NAME;
                    }

                    BusinessCalendar businessCalendar = CommandContextUtil.getProcessEngineConfiguration(commandContext).getBusinessCalendarManager()
                            .getBusinessCalendar(businessCalendarName);
                    task.setDueDate(businessCalendar.resolveDuedate((String) dueDate));

                } else {
                    throw new FlowableIllegalArgumentException("Due date expression does not resolve to a Date or Date string: " + activeTaskDueDate);
                }
            }
        }

        if (StringUtils.isNotEmpty(activeTaskPriority)) {
            final Object priority = expressionManager.createExpression(activeTaskPriority).getValue(execution);
            if (priority != null) {
                if (priority instanceof String) {
                    try {
                        task.setPriority(Integer.valueOf((String) priority));
                    } catch (NumberFormatException e) {
                        throw new FlowableIllegalArgumentException("Priority does not resolve to a number: " + priority, e);
                    }
                } else if (priority instanceof Number) {
                    task.setPriority(((Number) priority).intValue());
                } else {
                    throw new FlowableIllegalArgumentException("Priority expression does not resolve to a number: " + activeTaskPriority);
                }
            }
        }

        if (StringUtils.isNotEmpty(activeTaskCategory)) {
            String category = null;
            try {
                Object categoryValue = expressionManager.createExpression(activeTaskCategory).getValue(execution);
                if (categoryValue != null) {
                    category = categoryValue.toString();
                }
            }  catch (FlowableException e) {
                category = activeTaskCategory;
                LOGGER.warn("property not found in task category expression {}", e.getMessage());
            }
            task.setCategory(category.toString());
        }

        if (StringUtils.isNotEmpty(activeTaskFormKey)) {
            String formKey = null;
            try {
                Object formKeyValue = expressionManager.createExpression(activeTaskFormKey).getValue(execution);
                if (formKeyValue != null) {
                    formKey = formKeyValue.toString();
                }
            } catch (FlowableException e) {
                formKey = activeTaskFormKey;
                LOGGER.warn("property not found in task formKey expression {}", e.getMessage());
            }
            task.setFormKey(formKey.toString());
        }

        boolean skipUserTask = false;
        if (StringUtils.isNotEmpty(activeTaskSkipExpression)) {
            Expression skipExpression = expressionManager.createExpression(activeTaskSkipExpression);
            skipUserTask = SkipExpressionUtil.isSkipExpressionEnabled(execution, skipExpression)
                    && SkipExpressionUtil.shouldSkipFlowElement(execution, skipExpression);
        }
        if (!skipUserTask) {
            skipUserTask = customSkipUserTask((ExecutionEntity) execution,execution.getCurrentFlowElement());
        }
        
        TaskHelper.insertTask(task, (ExecutionEntity) execution, !skipUserTask);

        // Handling assignments need to be done after the task is inserted, to have an id
        if (!skipUserTask) {
            handleAssignments(taskService, activeTaskAssignee, activeTaskOwner,
                    activeTaskCandidateUsers, activeTaskCandidateGroups, task, expressionManager, execution);
            
            if (processEngineConfiguration.isEnableEntityLinks()) {
                EntityLinkUtil.copyExistingEntityLinks(execution.getProcessInstanceId(), task.getId(), ScopeTypes.TASK);
                EntityLinkUtil.createNewEntityLink(execution.getProcessInstanceId(), task.getId(), ScopeTypes.TASK);
            }
            
            processEngineConfiguration.getListenerNotificationHelper().executeTaskListeners(task, TaskListener.EVENTNAME_CREATE);

            // All properties set, now firing 'create' events
            if (CommandContextUtil.getTaskServiceConfiguration(commandContext).getEventDispatcher().isEnabled()) {
                CommandContextUtil.getTaskServiceConfiguration(commandContext).getEventDispatcher().dispatchEvent(
                        FlowableTaskEventBuilder.createEntityEvent(FlowableEngineEventType.TASK_CREATED, task));
            }
            
        } else {
            TaskHelper.deleteTask(task, null, false, false, false); // false: no events fired for skipped user task
            leave(execution);
        }
    }


    @Override
    public void deleteFlowTask(DelegateExecution execution) {
        String executionId = execution.getId();
        CommandContext commandContext = CommandContextUtil.getCommandContext();
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        TaskService taskService = CommandContextUtil.getTaskService(commandContext);
        List<TaskEntity> tasks  = taskService.findTasksByExecutionId(executionId);
        if (tasks!=null) {
            tasks.stream().forEach(task->{
                TaskHelper.deleteTask(task, "delete", true, false, false); // false: no events fired for skipped user task
            });
        }
    }

    @Override
    public void jumpToTargetFlowElement(DelegateExecution execution, FlowElement flowElement) {
        deleteFlowTask(execution);
        execution.setCurrentFlowElement(flowElement);
        CommandContextUtil.getAgenda().planContinueProcessInCompensation((ExecutionEntity) execution);
    }

    @Override
    public void updateFlowTask(DelegateExecution execution,FlowElement flowElement) {
        List<TaskEntity> taskEntities = CommandContextUtil.getTaskService().findTasksByExecutionId(execution.getId()); // Should be only one
        TaskEntity currentTask = null;
        for (TaskEntity taskEntity : taskEntities) {
            if (!taskEntity.isDeleted()) {
                throw new FlowableException("UserTask should not be signalled before complete");
            }
            currentTask = taskEntity;
        }
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration();
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
        UserTask userTask = (UserTask) flowElement;
        List<String> candidateUsers = userTask.getCandidateUsers();
        CommandContextUtil.getIdentityLinkService().deleteIdentityLinksByTaskIdImmediately(currentTask.getId());
        if (candidateUsers != null && !candidateUsers.isEmpty()) {
            for (String candidateUser : candidateUsers) {
                Expression userIdExpr = expressionManager.createExpression(candidateUser);
                Object value = userIdExpr.getValue(execution);
                if (value != null) {
                    if (value instanceof Collection) {
                        List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(currentTask.getId(), (Collection) value);
                        IdentityLinkUtil.handleTaskIdentityLinkAdditions(currentTask, identityLinkEntities);

                    } else {
                        String strValue = value.toString();
                        if (StringUtils.isNotEmpty(strValue)) {
                            List<String> candidates = extractCandidates(strValue);
                            List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(currentTask.getId(), candidates);
                            IdentityLinkUtil.handleTaskIdentityLinkAdditions(currentTask, identityLinkEntities);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void trigger(DelegateExecution execution, String signalName, Object signalData) {
        List<TaskEntity> taskEntities = CommandContextUtil.getTaskService().findTasksByExecutionId(execution.getId()); // Should be only one
        for (TaskEntity taskEntity : taskEntities) {
            if (!taskEntity.isDeleted()) {
                throw new FlowableException("UserTask should not be signalled before complete");
            }
        }

        leave(execution);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void handleAssignments(TaskService taskService, String assignee, String owner, List<String> candidateUsers,
            List<String> candidateGroups, TaskEntity task, ExpressionManager expressionManager, DelegateExecution execution) {

        if (StringUtils.isNotEmpty(assignee)) {
            Object assigneeExpressionValue = expressionManager.createExpression(assignee).getValue(execution);
            String assigneeValue = null;
            if (assigneeExpressionValue != null) {
                assigneeValue = assigneeExpressionValue.toString();
            }

            if (StringUtils.isNotEmpty(assigneeValue)) {
                TaskHelper.changeTaskAssignee(task, assigneeValue);
            }
        }

        if (StringUtils.isNotEmpty(owner)) {
            Object ownerExpressionValue = expressionManager.createExpression(owner).getValue(execution);
            String ownerValue = null;
            if (ownerExpressionValue != null) {
                ownerValue = ownerExpressionValue.toString();
            }

            if (StringUtils.isNotEmpty(ownerValue)) {
                TaskHelper.changeTaskOwner(task, ownerValue);
            }
        }
        boolean noCandidateUsers = true;

        if (candidateGroups != null && !candidateGroups.isEmpty()) {
            for (String candidateGroup : candidateGroups) {
                Expression groupIdExpr = expressionManager.createExpression(candidateGroup);
                Object value = groupIdExpr.getValue(execution);
                if (value != null) {
                    if (value instanceof Collection) {
                        List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateGroups(task.getId(), (Collection) value);
                        IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);
                        
                    } else {
                        String strValue = value.toString();
                        if (StringUtils.isNotEmpty(strValue)) {
                            List<String> candidates = extractCandidates(strValue);
                            List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateGroups(task.getId(), candidates);
                            IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);
                        }
                    }
                    noCandidateUsers = false;
                }
            }
        }

        if (candidateUsers != null && !candidateUsers.isEmpty()) {
            for (String candidateUser : candidateUsers) {
                Expression userIdExpr = expressionManager.createExpression(candidateUser);
                Object value = userIdExpr.getValue(execution);
                if (value != null) {
                    if (value instanceof Collection) {
                        List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(task.getId(), (Collection) value);
                        IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);

                    } else {
                        String strValue = value.toString();
                        if (StringUtils.isNotEmpty(strValue)) {
                            List<String> candidates = extractCandidates(strValue);
                            List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(task.getId(), candidates);
                            IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);
                        }
                        
                    }
                    noCandidateUsers = false;
                }
            }
        }

        if (userTask.getCustomUserIdentityLinks() != null && !userTask.getCustomUserIdentityLinks().isEmpty()) {

            for (String customUserIdentityLinkType : userTask.getCustomUserIdentityLinks().keySet()) {
                for (String userIdentityLink : userTask.getCustomUserIdentityLinks().get(customUserIdentityLinkType)) {
                    Expression idExpression = expressionManager.createExpression(userIdentityLink);
                    Object value = idExpression.getValue(execution);
                    if (value instanceof Collection) {
                        Iterator userIdSet = ((Collection) value).iterator();
                        while (userIdSet.hasNext()) {
                            IdentityLinkEntity identityLinkEntity = CommandContextUtil.getIdentityLinkService().createTaskIdentityLink(
                                            task.getId(), userIdSet.next().toString(), null, customUserIdentityLinkType);
                            IdentityLinkUtil.handleTaskIdentityLinkAddition(task, identityLinkEntity);
                        }
                        
                    } else {
                        List<String> userIds = extractCandidates(value.toString());
                        for (String userId : userIds) {
                            IdentityLinkEntity identityLinkEntity = CommandContextUtil.getIdentityLinkService().createTaskIdentityLink(task.getId(), userId, null, customUserIdentityLinkType);
                            IdentityLinkUtil.handleTaskIdentityLinkAddition(task, identityLinkEntity);
                        }
                        
                    }

                }
            }

        }

        if (userTask.getCustomGroupIdentityLinks() != null && !userTask.getCustomGroupIdentityLinks().isEmpty()) {

            for (String customGroupIdentityLinkType : userTask.getCustomGroupIdentityLinks().keySet()) {
                for (String groupIdentityLink : userTask.getCustomGroupIdentityLinks().get(customGroupIdentityLinkType)) {

                    Expression idExpression = expressionManager.createExpression(groupIdentityLink);
                    Object value = idExpression.getValue(execution);
                    if (value instanceof Collection) {
                        Iterator groupIdSet = ((Collection) value).iterator();
                        while (groupIdSet.hasNext()) {
                            IdentityLinkEntity identityLinkEntity = CommandContextUtil.getIdentityLinkService().createTaskIdentityLink(
                                            task.getId(), null, groupIdSet.next().toString(), customGroupIdentityLinkType);
                            IdentityLinkUtil.handleTaskIdentityLinkAddition(task, identityLinkEntity);
                        }
                        
                    } else {
                        List<String> groupIds = extractCandidates(value.toString());
                        for (String groupId : groupIds) {
                            IdentityLinkEntity identityLinkEntity = CommandContextUtil.getIdentityLinkService().createTaskIdentityLink(
                                            task.getId(), null, groupId, customGroupIdentityLinkType);
                            IdentityLinkUtil.handleTaskIdentityLinkAddition(task, identityLinkEntity);
                        }
                        
                    }

                }
            }

        }
        //TODO 审批人为空要修改
        if (noCandidateUsers && !dealCustomCandidateUsers(task,expressionManager,execution)) {
            dealApproverNoStrategy(task,expressionManager,execution);
        }

    }

    protected void multiInstanceExcute(DelegateExecution execution,int index) {
        List<String> activeTaskCandidateUsers =  getOrderCandidateUserExpression(execution,index);
        CommandContext commandContext = CommandContextUtil.getCommandContext();
        TaskService taskService = CommandContextUtil.getTaskService(commandContext);

        TaskEntity task = taskService.createTask();
        task.setExecutionId(execution.getId());
        task.setTaskDefinitionKey(userTask.getId());

        String activeTaskName = null;
        String activeTaskDescription = null;
        String activeTaskDueDate = null;
        String activeTaskPriority = null;
        String activeTaskCategory = null;
        String activeTaskFormKey = null;
        String activeTaskSkipExpression = null;
        String activeTaskAssignee = null;
        String activeTaskOwner = null;
        String activeTaskSkipStrategy = null;
        String activeTaskSkipStrategyExpression = null;

        List<String> activeTaskCandidateGroups = null;

        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();

        if (CommandContextUtil.getProcessEngineConfiguration(commandContext).isEnableProcessDefinitionInfoCache()) {
            ObjectNode taskElementProperties = BpmnOverrideContext.getBpmnOverrideElementProperties(userTask.getId(), execution.getProcessDefinitionId());
            activeTaskName = getActiveValue(userTask.getName(), DynamicBpmnConstants.USER_TASK_NAME, taskElementProperties);
            activeTaskDescription = getActiveValue(userTask.getDocumentation(), DynamicBpmnConstants.USER_TASK_DESCRIPTION, taskElementProperties);
            activeTaskDueDate = getActiveValue(userTask.getDueDate(), DynamicBpmnConstants.USER_TASK_DUEDATE, taskElementProperties);
            activeTaskPriority = getActiveValue(userTask.getPriority(), DynamicBpmnConstants.USER_TASK_PRIORITY, taskElementProperties);
            activeTaskCategory = getActiveValue(userTask.getCategory(), DynamicBpmnConstants.USER_TASK_CATEGORY, taskElementProperties);
            activeTaskFormKey = getActiveValue(userTask.getFormKey(), DynamicBpmnConstants.USER_TASK_FORM_KEY, taskElementProperties);
            activeTaskSkipExpression = getActiveValue(userTask.getSkipExpression(), DynamicBpmnConstants.TASK_SKIP_EXPRESSION, taskElementProperties);
            activeTaskAssignee = getActiveValue(userTask.getAssignee(), DynamicBpmnConstants.USER_TASK_ASSIGNEE, taskElementProperties);
            activeTaskOwner = getActiveValue(userTask.getOwner(), DynamicBpmnConstants.USER_TASK_OWNER, taskElementProperties);
        } else {
            activeTaskName = userTask.getName();
            activeTaskDescription = userTask.getDocumentation();
            activeTaskDueDate = userTask.getDueDate();
            activeTaskPriority = userTask.getPriority();
            activeTaskCategory = userTask.getCategory();
            activeTaskFormKey = userTask.getFormKey();
            activeTaskSkipExpression = userTask.getSkipExpression();
            activeTaskAssignee = userTask.getAssignee();
            activeTaskOwner = userTask.getOwner();
        }

        if (StringUtils.isNotEmpty(activeTaskName)) {
            String name = null;
            try {
                Object nameValue = expressionManager.createExpression(activeTaskName).getValue(execution);
                if (nameValue != null) {
                    name = nameValue.toString();
                }
            } catch (FlowableException e) {
                name = activeTaskName;
                LOGGER.warn("property not found in task name expression {}", e.getMessage());
            }
            task.setName(name);
        }

        if (StringUtils.isNotEmpty(activeTaskDescription)) {
            String description = null;
            try {
                Object descriptionValue = expressionManager.createExpression(activeTaskDescription).getValue(execution);
                if (descriptionValue != null) {
                    description = descriptionValue.toString();
                }
            } catch (FlowableException e) {
                description = activeTaskDescription;
                LOGGER.warn("property not found in task description expression {}", e.getMessage());
            }
            task.setDescription(description);
        }

        if (StringUtils.isNotEmpty(activeTaskDueDate)) {
            Object dueDate = expressionManager.createExpression(activeTaskDueDate).getValue(execution);
            if (dueDate != null) {
                if (dueDate instanceof Date) {
                    task.setDueDate((Date) dueDate);
                } else if (dueDate instanceof String) {
                    String businessCalendarName = null;
                    if (StringUtils.isNotEmpty(userTask.getBusinessCalendarName())) {
                        businessCalendarName = expressionManager.createExpression(userTask.getBusinessCalendarName()).getValue(execution).toString();
                    } else {
                        businessCalendarName = DueDateBusinessCalendar.NAME;
                    }

                    BusinessCalendar businessCalendar = CommandContextUtil.getProcessEngineConfiguration(commandContext).getBusinessCalendarManager()
                            .getBusinessCalendar(businessCalendarName);
                    task.setDueDate(businessCalendar.resolveDuedate((String) dueDate));

                } else {
                    throw new FlowableIllegalArgumentException("Due date expression does not resolve to a Date or Date string: " + activeTaskDueDate);
                }
            }
        }

        if (StringUtils.isNotEmpty(activeTaskPriority)) {
            final Object priority = expressionManager.createExpression(activeTaskPriority).getValue(execution);
            if (priority != null) {
                if (priority instanceof String) {
                    try {
                        task.setPriority(Integer.valueOf((String) priority));
                    } catch (NumberFormatException e) {
                        throw new FlowableIllegalArgumentException("Priority does not resolve to a number: " + priority, e);
                    }
                } else if (priority instanceof Number) {
                    task.setPriority(((Number) priority).intValue());
                } else {
                    throw new FlowableIllegalArgumentException("Priority expression does not resolve to a number: " + activeTaskPriority);
                }
            }
        }

        if (StringUtils.isNotEmpty(activeTaskCategory)) {
            String category = null;
            try {
                Object categoryValue = expressionManager.createExpression(activeTaskCategory).getValue(execution);
                if (categoryValue != null) {
                    category = categoryValue.toString();
                }
            }  catch (FlowableException e) {
                category = activeTaskCategory;
                LOGGER.warn("property not found in task category expression {}", e.getMessage());
            }
            task.setCategory(category.toString());
        }

        if (StringUtils.isNotEmpty(activeTaskFormKey)) {
            String formKey = null;
            try {
                Object formKeyValue = expressionManager.createExpression(activeTaskFormKey).getValue(execution);
                if (formKeyValue != null) {
                    formKey = formKeyValue.toString();
                }
            } catch (FlowableException e) {
                formKey = activeTaskFormKey;
                LOGGER.warn("property not found in task formKey expression {}", e.getMessage());
            }
            task.setFormKey(formKey.toString());
        }

        boolean skipUserTask = false;
        if (StringUtils.isNotEmpty(activeTaskSkipExpression)) {
            Expression skipExpression = expressionManager.createExpression(activeTaskSkipExpression);
            skipUserTask = SkipExpressionUtil.isSkipExpressionEnabled(execution, skipExpression)
                    && SkipExpressionUtil.shouldSkipFlowElement(execution, skipExpression);
        }

        TaskHelper.insertTask(task, (ExecutionEntity) execution, !skipUserTask);

        // Handling assignments need to be done after the task is inserted, to have an id
        if (!skipUserTask) {
            handleAssignments(taskService, activeTaskAssignee, activeTaskOwner,
                    activeTaskCandidateUsers, activeTaskCandidateGroups, task, expressionManager, execution);

            if (processEngineConfiguration.isEnableEntityLinks()) {
                EntityLinkUtil.copyExistingEntityLinks(execution.getProcessInstanceId(), task.getId(), ScopeTypes.TASK);
                EntityLinkUtil.createNewEntityLink(execution.getProcessInstanceId(), task.getId(), ScopeTypes.TASK);
            }

            processEngineConfiguration.getListenerNotificationHelper().executeTaskListeners(task, TaskListener.EVENTNAME_CREATE);

            // All properties set, now firing 'create' events
            if (CommandContextUtil.getTaskServiceConfiguration(commandContext).getEventDispatcher().isEnabled()) {
                CommandContextUtil.getTaskServiceConfiguration(commandContext).getEventDispatcher().dispatchEvent(
                        FlowableTaskEventBuilder.createEntityEvent(FlowableEngineEventType.TASK_CREATED, task));
            }

        } else {
            TaskHelper.deleteTask(task, null, false, false, false); // false: no events fired for skipped user task
            leave(execution);
        }
    }

    public List<String> getOrderCandidateUserExpression(DelegateExecution execution, int index) {
        List<String> activeCandidateUserExpressions = new ArrayList<>();
        String activeCandidateUserExpression =  getCandidateUsersByIndex(execution,index);
        activeCandidateUserExpressions.add(activeCandidateUserExpression);
        return  activeCandidateUserExpressions;
    }

    protected ApproverNoStrategyProperties getApproverNoStrategy() {
        return  null;
    }

    private boolean dealApproverNoStrategy(TaskEntity task, ExpressionManager expressionManager, DelegateExecution execution) {
        //跳过
        ApproverNoStrategyProperties approverNoStrategy = getApproverNoStrategy();
        if (approverNoStrategy==null) {
            return  false;
        }
        if ("0".equals(approverNoStrategy.getApproverNoStrategy()) || "true".equals(approverNoStrategy.getApproverNoStrategy())) {
            TaskHelper.deleteTask(task, null, false, false, false);
            this.leave(execution);
            return true;
        } else if (StringUtils.isNotBlank(approverNoStrategy.getApproverNoStrategy())) {
            Expression userIdExpr = expressionManager.createExpression(approverNoStrategy.getApproverNoExpression());
            Object value = userIdExpr.getValue(execution);
            if (value != null) {
                if (value instanceof Collection) {
                    List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(task.getId(), (Collection) value);
                    IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);
                } else {
                    String strValue = value.toString();
                    if (org.apache.commons.lang3.StringUtils.isNotEmpty(strValue)) {
                        List<String> candidates = extractCandidates(strValue);
                        List<IdentityLinkEntity> identityLinkEntities = CommandContextUtil.getIdentityLinkService().addCandidateUsers(task.getId(), candidates);
                        IdentityLinkUtil.handleTaskIdentityLinkAdditions(task, identityLinkEntities);
                    }
                }
            }
            return true;
            // 插入该环节的候选人
        }
        return false;
    }

    private Set<String> getApproverNoStrageyCandidateUsers(DelegateExecution execution) {
        //跳过
        ApproverNoStrategyProperties approverNoStrategy = getApproverNoStrategy();
        if (approverNoStrategy==null) {
            return  null;
        }
        if ("0".equals(approverNoStrategy.getApproverNoStrategy()) || "true".equals(approverNoStrategy.getApproverNoStrategy())) {
            return null;
        } else if (StringUtils.isNotBlank(approverNoStrategy.getApproverNoStrategy())) {
            Set<String> candidateUsers = new HashSet<>();
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
            ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
            Expression userIdExpr = expressionManager.createExpression(approverNoStrategy.getApproverNoExpression());
            Object value = userIdExpr.getValue(execution);
            if (value != null) {
                if (value instanceof Collection) {
                    for (Object candidateUser : (Collection)value) {
                        if (candidateUser!=null) {
                            candidateUsers.add(candidateUser.toString());
                        }
                    }
                } else {
                    String strValue = value.toString();
                    if (org.apache.commons.lang3.StringUtils.isNotEmpty(strValue)) {
                        List<String> candidates = extractCandidates(strValue);
                        for (String candidateUser : candidates) {
                            candidateUsers.add(candidateUser);
                        }
                    }
                }
            }
            return candidateUsers;
            // 插入该环节的候选人
        }
        return null;
    }


    /**
     * Extract a candidate list from a string.
     * 
     * @param str
     * @return
     */
    protected List<String> extractCandidates(String str) {
        return Arrays.asList(str.split("[\\s]*,[\\s]*"));
    }


    protected List<String> getCandidateUsers (DelegateExecution execution) {
        Set<String> candidateUserSet = new HashSet<>();
        List<String> activeCandidateUserExpressions = null;
        CommandContext context = CommandContextUtil.getCommandContext();
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(context);
        ExpressionManager expressionManager = processEngineConfiguration.getExpressionManager();
        if (Context.getProcessEngineConfiguration().isEnableProcessDefinitionInfoCache()) {
            ObjectNode taskElementProperties = BpmnOverrideContext.getBpmnOverrideElementProperties(userTask.getId(), execution.getProcessDefinitionId());
            activeCandidateUserExpressions = getActiveValueList(userTask.getCandidateUsers(), DynamicBpmnConstants.USER_TASK_CANDIDATE_USERS, taskElementProperties);
        } else {
            activeCandidateUserExpressions = userTask.getCandidateUsers();
        }
        for (String  candidateUser: activeCandidateUserExpressions) {
            Expression userIdExpr = expressionManager.createExpression(candidateUser);
            Object value = userIdExpr.getValue(execution);
            if (value != null) {
                if (value instanceof Collection) {
                    for (Object candidateUserTemp : (Collection)value) {
                        candidateUserSet.add(candidateUserTemp.toString());
                    }
                } else {
                    String strValue = value.toString();
                    if (org.apache.commons.lang3.StringUtils.isNotEmpty(strValue)) {
                        List<String> candidates = extractCandidates(strValue);
                        for (Object candidateUserTemp : candidates) {
                            candidateUserSet.add(candidateUserTemp.toString());
                        }
                    }
                }
            }
        }
        Set<String> otherCandidateUsers = getCustomCandidateUsers(execution);
        if (otherCandidateUsers!=null && !otherCandidateUsers.isEmpty()) {
            candidateUserSet.addAll(otherCandidateUsers);
        }
        if (candidateUserSet.isEmpty()) {
            Set<String> approverNoCandidates = getApproverNoStrageyCandidateUsers(execution);
            if (approverNoCandidates!=null && !approverNoCandidates.isEmpty()) {
                candidateUserSet.addAll(approverNoCandidates);
            }
        }
        // 处理内部数据
        return candidateUserSet.stream().collect(Collectors.toList());
    }

    protected Set<String> getCustomCandidateUsers(DelegateExecution execution) {
        //doNothing 留个子类来实现
        return null;
    }

    protected boolean dealCustomCandidateUsers(TaskEntity task, ExpressionManager expressionManager, DelegateExecution execution) {
        //doNothing 留个子类来实现
        return false;

    }
}