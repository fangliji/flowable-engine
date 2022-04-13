package org.flowable.engine.impl.persistence.entity.data;

import org.flowable.common.engine.impl.persistence.entity.data.DataManager;
import org.flowable.engine.impl.persistence.entity.ModelEntity;
import org.flowable.engine.impl.persistence.entity.ProcessEntity;

/**
 * 类名称：ProcessDataManager
 * 类描述：TODO
 * 创建时间：4/12/22 5:21 PM
 * 创建人：flj
 */
public interface ProcessDataManager extends DataManager<ProcessEntity> {
    ProcessEntity getProcessByInstanceId(String processInstanceId);
}
