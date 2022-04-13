package org.flowable.engine.impl.persistence.entity.data.impl;

import org.flowable.bpmn.model.Process;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ModelEntity;
import org.flowable.engine.impl.persistence.entity.ModelEntityImpl;
import org.flowable.engine.impl.persistence.entity.ProcessEntity;
import org.flowable.engine.impl.persistence.entity.ProcessEntityImpl;
import org.flowable.engine.impl.persistence.entity.data.AbstractProcessDataManager;
import org.flowable.engine.impl.persistence.entity.data.ProcessDataManager;

/**
 * 类名称：MybatisProcessDataManager
 * 类描述：TODO
 * 创建时间：4/12/22 5:28 PM
 * 创建人：flj
 */
public class MybatisProcessDataManager extends AbstractProcessDataManager<ProcessEntity> implements ProcessDataManager {
    public MybatisProcessDataManager(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    @Override
    public Class<? extends ProcessEntity> getManagedEntityClass() {
        return ProcessEntityImpl.class;
    }


    @Override
    public ProcessEntityImpl create() {
        return new ProcessEntityImpl();
    }

    @Override
    public ProcessEntity getProcessByInstanceId(String processInstanceId) {

        return (ProcessEntity) getDbSqlSession().selectOne("selectProcessByInstanceId", processInstanceId);
    };

}
