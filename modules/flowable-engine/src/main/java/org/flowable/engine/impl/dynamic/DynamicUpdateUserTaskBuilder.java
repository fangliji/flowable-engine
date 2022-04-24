package org.flowable.engine.impl.dynamic;

import org.flowable.bpmn.model.FlowElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 类名称：DynamicUpdateUserTaskBuilder
 * 类描述：TODO
 * 创建时间：4/14/22 11:42 AM
 * 创建人：flj
 */
public class DynamicUpdateUserTaskBuilder {
    protected String taskKey;
    protected List<String> candidateUsers = new ArrayList<>();
    protected int flag;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public List<String> getCandidateUsers() {
        return candidateUsers;
    }

    public void setCandidateUsers(List<String> candidateUsers) {
        this.candidateUsers = candidateUsers;
    }
}
