package org.flowable.engine.impl.dynamic;

import org.flowable.bpmn.model.FlowElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类名称：DynamicAddUserTaskBuilder
 * 类描述：TODO
 * 创建时间：4/14/22 7:55 PM
 * 创建人：flj
 */
public class DynamicAddUserTaskBuilder {
    protected String assignee;
    protected String owner;
    protected String priority;
    protected String formKey;
    protected String dueDate;
    protected String category;
    protected List<String> candidateUsers = new ArrayList<>();
    protected String name;
    // 加节点方式 before 之前加签 after 之后加签
    protected String addWay;
    // 在那个接点前后加
    protected String taskKey;
    protected int counter = 1;

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getFormKey() {
        return formKey;
    }

    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getCandidateUsers() {
        return candidateUsers;
    }

    public void setCandidateUsers(List<String> candidateUsers) {
        this.candidateUsers = candidateUsers;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddWay() {
        return addWay;
    }

    public void setAddWay(String addWay) {
        this.addWay = addWay;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String nextTaskId(Map<String, FlowElement> flowElementMap) {
        return nextId("dynamicTask", flowElementMap);
    }

    public String nextFlowId(Map<String, FlowElement> flowElementMap) {
        return nextId("dynamicFlow", flowElementMap);
    }

    protected String nextId(String prefix, Map<String, FlowElement> flowElementMap) {
        String nextId = null;
        boolean nextIdNotFound = true;
        while (nextIdNotFound) {
            if (!flowElementMap.containsKey(prefix + counter)) {
                nextId = prefix + counter;
                nextIdNotFound = false;
            }

            counter++;
        }

        return nextId;
    }
}
