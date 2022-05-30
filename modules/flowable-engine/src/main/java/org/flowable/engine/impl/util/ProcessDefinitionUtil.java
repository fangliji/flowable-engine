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

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.repository.EngineDeployment;
import org.flowable.common.engine.api.repository.EngineResource;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.persistence.deploy.DeploymentCache;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.flowable.engine.impl.bpmn.deployer.ParsedDeploymentBuilderFactory;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.BpmnParser;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.deploy.DeploymentManager;
import org.flowable.engine.impl.persistence.deploy.ProcessDefinitionCacheEntry;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntityManager;
import org.flowable.engine.impl.persistence.entity.ProcessEntity;
import org.flowable.engine.impl.persistence.entity.ProcessEntityImpl;
import org.flowable.engine.impl.persistence.entity.data.ProcessDataManager;
import org.flowable.engine.impl.redis.RedisWorker;
import org.flowable.engine.repository.ProcessDefinition;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Map;

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

    /*public static Process getProcess(String processDefinitionId) {
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
    }*/

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
       try {
            BpmnModel bpmnModel = getBpmnModelFromProcessEntity(processInstanceId,true);
            if (bpmnModel!=null) {
                return bpmnModel.getMainProcess();
           }
       } catch (Exception e) {
            throw new FlowableException(e.getMessage());
       }
       DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
        // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
       ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
       return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getProcess();

    }



    private static EngineDeployment createEngineDeployment(String processInstanceId) {
        return new EngineDeployment() {
            @Override
            public String getId() {
                return processInstanceId;
            }

            @Override
            public String getName() {
                return processInstanceId;
            }

            @Override
            public Date getDeploymentTime() {
                return null;
            }

            @Override
            public String getCategory() {
                return null;
            }

            @Override
            public String getKey() {
                return null;
            }

            @Override
            public String getDerivedFrom() {
                return null;
            }

            @Override
            public String getDerivedFromRoot() {
                return null;
            }

            @Override
            public String getTenantId() {
                return null;
            }

            @Override
            public String getEngineVersion() {
                return null;
            }

            @Override
            public boolean isNew() {
                return false;
            }

            @Override
            public Map<String, EngineResource> getResources() {
                return null;
            }
        };
    }

    public static BpmnModel getBpmnModel(String processInstanceId,String processDefinitionId,boolean isFromDb,boolean isRead) {

        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");

        }  else if (processInstanceId ==null) {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
            if (isFromDb) {
                return deploymentManager.getBpmnModelByIdFromDb(processDefinitionId);
            }
            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
        try {
            BpmnModel bpmnModel = getBpmnModelFromProcessEntity(processInstanceId,isRead);
            if (bpmnModel!=null) {
                return bpmnModel;
            }
        } catch (Exception e) {
            throw new FlowableException(e.getMessage());
        }
        DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
        if (isFromDb) {
            return deploymentManager.getBpmnModelByIdFromDb(processDefinitionId);
        }
        // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
        ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
        return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();

    }

    public static BpmnModel getBpmnModel(String processInstanceId,String processDefinitionId,boolean isFromDb) {

        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");

        }  else if (processInstanceId ==null) {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
            if (isFromDb) {
                return deploymentManager.getBpmnModelByIdFromDb(processDefinitionId);
            }
            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
        try {
            BpmnModel bpmnModel = getBpmnModelFromProcessEntity(processInstanceId,true);
            if (bpmnModel!=null) {
                return bpmnModel;
            }
        } catch (Exception e) {
            throw new FlowableException(e.getMessage());
        }
        DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();
        if (isFromDb) {
            return deploymentManager.getBpmnModelByIdFromDb(processDefinitionId);
        }
        // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
        ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
        return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();

    }

    public static BpmnModel getBpmnModelFromProcessEntity(String processInstanceId,boolean isRead) throws Exception{
        // 加锁
        RedisWorker redisWorker = CommandContextUtil.getProcessEngineConfiguration().getRedisWorker();

        boolean lock = false;
        String lockFlag = "PROCESSWRITE#"+ processInstanceId;
        // 默认判断当前是否先有写锁
        if (StringUtils.isNotBlank(redisWorker.get(lockFlag))) {
            throw new FlowableException("Cannot Get PROCESS "+processInstanceId+" lock");
        } else if (!isRead) {
            if (StringUtils.isNotBlank(redisWorker.get(String.join("PROCESSREAD#",processInstanceId)))) {
                throw new FlowableException("Cannot Get PROCESS "+processInstanceId+" lock");
            }
            lock = redisWorker.setIfAbsent(lockFlag, 15,String.join(processInstanceId,String.valueOf(System.currentTimeMillis())));
            if (!lock) {
                throw new FlowableException("Cannot Get PROCESS "+processInstanceId+" lock");
            }
        } else {
            // 读锁
            lockFlag = String.join("PROCESSREAD#",processInstanceId);
            lock = redisWorker.setIfAbsent(lockFlag, 15,String.join(processInstanceId,String.valueOf(System.currentTimeMillis())));
            if (!lock) {
                // 续约
                redisWorker.expire(lockFlag,15);
            }
        }

        try {
            ProcessDataManager processDataManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDataManager();
            ProcessEntity processEntity = processDataManager.getProcessByInstanceId(processInstanceId);
            if (processEntity!=null ) {
                ParsedDeploymentBuilderFactory  parsedDeploymentBuilderFactory = CommandContextUtil.getProcessEngineConfiguration().getParsedDeploymentBuilderFactory();
                BpmnParser bpmnParser = parsedDeploymentBuilderFactory.getBpmnParser();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(processEntity.getBytes());
                EngineDeployment deployment = createEngineDeployment(processInstanceId);
                String resourceName = processInstanceId;
                BpmnParse bpmnParse = bpmnParser.createParse()
                        .sourceInputStream(byteArrayInputStream)
                        .setSourceSystemId(resourceName)
                        .deployment(deployment)
                        .name(resourceName);
                    bpmnParse.execute();
                BpmnModel bpmnModel = bpmnParse.getBpmnModel();
                return bpmnModel;
            }
        } catch (Exception e) {
            throw new FlowableException(e.getMessage());
        } finally {
            if (lock) {
                redisWorker.delete(lockFlag);
            }
        }
        return null;
    }


    public static void updateProcess(String processInstanceId,BpmnModel bpmnModel) throws Exception{
        //TODO 加锁
        if (Context.getCommandContext() == null) {
            throw new FlowableException("Cannot get process model: no current command context is active");

        }
        RedisWorker redisWorker = CommandContextUtil.getProcessEngineConfiguration().getRedisWorker();
        // 加锁
        boolean lock = false;
        String lockFlag = "PROCESSWRITE#"+ processInstanceId;
        if (StringUtils.isNotBlank(redisWorker.get(String.join("PROCESSREAD#",processInstanceId)))) {
            throw new FlowableException("Cannot Get PROCESS "+processInstanceId+" lock");
        }
        lock = redisWorker.setIfAbsent(lockFlag, 15,String.join(processInstanceId,String.valueOf(System.currentTimeMillis())));
        if (!lock) {
            throw new FlowableException("Cannot Get PROCESS "+processInstanceId+" lock");
        }
        try {
            ProcessDataManager processDataManager = CommandContextUtil.getProcessEngineConfiguration().getProcessDataManager();
            ProcessEntity processEntity = processDataManager.getProcessByInstanceId(processInstanceId);
            byte[] bytes = bpmnXMLConverter.convertToXML(bpmnModel);
            if (processEntity == null) {
                processEntity = new ProcessEntityImpl();
                processEntity.setId(processInstanceId);
                processEntity.setProcInstId(processInstanceId);
                processEntity.setRevision(1);
                processEntity.setBytes(bytes);
                processDataManager.createImmediately(processEntity);
            } else {
                processEntity.setBytes(bytes);
                processDataManager.updateImmediately(processEntity);
            }
        } finally {
          if (lock) {
              redisWorker.delete(lockFlag);
          }
        }
    }

    /*public static BpmnModel getBpmnModel(String processDefinitionId) {
        if (CommandContextUtil.getProcessEngineConfiguration() == null) {
            return Flowable5Util.getFlowable5CompatibilityHandler().getProcessDefinitionBpmnModel(processDefinitionId);

        } else {
            DeploymentManager deploymentManager = CommandContextUtil.getProcessEngineConfiguration().getDeploymentManager();

            // This will check the cache in the findDeployedProcessDefinitionById and resolveProcessDefinition method
            ProcessDefinition processDefinitionEntity = deploymentManager.findDeployedProcessDefinitionById(processDefinitionId);
            return deploymentManager.resolveProcessDefinition(processDefinitionEntity).getBpmnModel();
        }
    }*/


  /*  public static BpmnModel getBpmnModelFromCache(String processDefinitionId) {
        ProcessDefinitionCacheEntry cacheEntry = CommandContextUtil.getProcessEngineConfiguration().getProcessDefinitionCache().get(processDefinitionId);
        if (cacheEntry != null) {
            return cacheEntry.getBpmnModel();
        }
        return null;
    }*/

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
