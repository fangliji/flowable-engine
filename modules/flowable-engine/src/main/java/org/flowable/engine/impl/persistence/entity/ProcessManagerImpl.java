package org.flowable.engine.impl.persistence.entity;

import org.flowable.common.engine.impl.persistence.entity.data.DataManager;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.data.ProcessDataManager;

/**
 * 类名称：ProcessManagerImpl
 * 类描述：TODO
 * 创建时间：4/12/22 5:18 PM
 * 创建人：flj
 */
public class ProcessManagerImpl extends
        AbstractEntityManager<ProcessEntity> implements ProcessManager  {
    protected ProcessDataManager processDataManager;

    public ProcessManagerImpl(ProcessEngineConfigurationImpl processEngineConfiguration, ProcessDataManager processDataManager) {
        super(processEngineConfiguration);
        this.processDataManager = processDataManager;
    }

    @Override
    protected DataManager<ProcessEntity> getDataManager() {
        return processDataManager;
    }
}
