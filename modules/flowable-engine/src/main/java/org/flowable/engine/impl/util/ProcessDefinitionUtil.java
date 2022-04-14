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
package org.flowable.engine.impl.util;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.deploy.DeploymentManager;
import org.flowable.engine.impl.persistence.deploy.ProcessDefinitionCacheEntry;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntityManager;
import org.flowable.engine.impl.persistence.entity.ProcessEntity;
import org.flowable.engine.impl.persistence.entity.ProcessEntityImpl;
import org.flowable.engine.impl.persistence.entity.data.ProcessDataManager;
import org.flowable.engine.repository.ProcessDefinition;

import java.io.ByteArrayInputStream;

/**
 * A utility class that hides the complexity of {@link ProcessDefinitionEntity} and {@link Process} lookup. Use this class rather than accessing the process definition cache or
 * {@link DeploymentManager} directly.
 * 
 * @author Joram Barrez
 */
public class ProcessDefinitionUtil {

     static  BpmnXMLConverter  bpmnXMLConverter = new BpmnXMLConverter();

    public static ProcessDefinition getProcessDefinition(String processDefinitionId) {
        return getProcessDefinition(processDefinitionId, false);
    }

    public static ProcessDefinition getProcessDefinition(String processDefinitionId, boolean checkCacheOnly) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration();
        if (checkCacheOnly) {
            ProcessDefinitionCacheEntry cacheEntry = processEngineConfiguration.getProcessDefinitionCache().get(processDefinitionId);
            if (cacheEntry != null) {
                return cacheEntry.getProcessDefinition();
            }
            return null;

        } else {
            // This will check the cache in the findDeployedProcessDefinitionById method
            return processEngineConfiguration.getDeploymentManager().findDeployedProcessDefinitionById(processDefinitionId);
        }
    }

    public static Process getProcess(String processDefinitionId) {
        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");
            
        } else if (CommandContextUtil.getProcessEngineConfiguration() == null) {
            return Flowable5Util.getFlowable5CompatibilityHandler().getProcessDefinitionProcessObject(processDefinitionId);

        } else {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();

            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getProcess();
        }
    }

    public static Process getProcess(String processInstanceId,String processDefinitionId) {
       if (Context.getCommandContext() == null) {
           throw new FlowableException("Cannot get process model: no current command context is active");

       } else if (CommandContextUtil.getProcessEngineConfiguration() == null) {
           return Flowable5Util.getFlowable5CompatibilityHandler().getProcessDefinitionProcessObject(processDefinitionId);

       } else if (processInstanceId ==null) {
           DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
           // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
           ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
           return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getProcess();
       }
       // TODO 加锁
       ProcessDataManager processDataManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDataManager();
       ProcessEntity processEntity = processDataManager.getProcessByInstanceId(processInstanceId);
       if (processEntity!=null ) {
           ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processEntity.getBytes());
           BpmnModel bpmnModel = bpmnXMLConverter.convertToBpmnModel(new InputStreamSource(byteArrayInputStream), true, false, "UTF-8");
           return bpmnModel.getMainProcess();
       } else {
           DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
           // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
           ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
           return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getProcess();
       }
    }

    public static BpmnModel getBpmnModel(String processInstanceId,String processDefinitionId) {
        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");

        }  else if (processInstanceId ==null) {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
        // TODO 加锁
        ProcessDataManager processDataManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDataManager();
        ProcessEntity processEntity = processDataManager.getProcessByInstanceId(processInstanceId);
        if (processEntity!=null ) {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processEntity.getBytes());
            BpmnModel bpmnModel = bpmnXMLConverter.convertToBpmnModel(new InputStreamSource(byteArrayInputStream), true, false, "UTF-8");
            return bpmnModel;
        } else {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
    }


    public static void updateProcess(String processInstanceId,BpmnModel bpmnModel) {
        //TODO 加锁
        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");

        }
        ProcessDataManager processDataManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDataManager();
        ProcessEntity processEntity = processDataManager.getProcessByInstanceId(processInstanceId);
        byte[] bytes = bpmnXMLConverter.convertToXML(bpmnModel);
        if (processEntity == null) {
            processEntity = new ProcessEntityImpl();
            processEntity.setId(processInstanceId);
            processEntity.setRevision(1);
            processEntity.setBytes(bytes);
            processDataManager.insert(processEntity);
        } else {
            processEntity.setBytes(bytes);
            processEntity.setRevision(processEntity.getRevisionNext());
            processDataManager.update(processEntity);
        }
    }

    public static BpmnModel getBpmnModel(String processDefinitionId) {
        if (CommandContextUtil.getProcessEngineConfiguration() == null) {
            return Flowable5Util.getFlowable5CompatibilityHandler().getProcessDefinitionBpmnModel(processDefinitionId);

        } else {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();

            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
    }

    public static BpmnModel getBpmnModelFromCache(String processDefinitionId) {
        ProcessDefinitionCacheEntry cacheEntry = CommandContextUtil.getProcessEngineConfiguration().getProcessDefinitionCache().get(processDefinitionId);
        if (cacheEntry != null) {
            return cacheEntry.getBpmnModel();
        }
        return null;
    }

    public static boolean isProcessDefinitionSuspended(String processDefinitionId) {
        ProcessDefinitionEntity processDefinition = getProcessDefinitionFromDatabase(processDefinitionId);
        return processDefinition.isSuspended();
    }

    public static ProcessDefinitionEntity getProcessDefinitionFromDatabase(String processDefinitionId) {
        ProcessDefinitionEntityManager processDefinitionEntityManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDefinitionEntityManager();
        ProcessDefinitionEntity processDefinition = processDefinitionEntityManager.findById(processDefinitionId);
        if (processDefinition == null) {
            throw new FlowableObjectNotFoundException("No process definition found with id " + processDefinitionId);
        }

        return processDefinition;
    }
}
