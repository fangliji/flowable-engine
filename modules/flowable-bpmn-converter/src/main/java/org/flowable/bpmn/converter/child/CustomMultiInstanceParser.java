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
package org.flowable.bpmn.converter.child;

import org.flowable.bpmn.converter.util.BpmnXMLUtil;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CustomMultiInstanceLoopCharacteristics;

import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tijs Rademakers
 */
public class CustomMultiInstanceParser extends BaseChildElementParser {

    @Override
    public String getElementName() {
        return CUSTOM_ELEMENT_MULTIINSTANCE;
    }

    @Override
    public void parseChildElement(XMLStreamReader xtr, BaseElement parentElement, BpmnModel model) throws Exception {
        if (!(parentElement instanceof Activity))
            return;
        //TODO 需要修改，需要修改
        CustomMultiInstanceLoopCharacteristics multiInstanceDef = new CustomMultiInstanceLoopCharacteristics();
        BpmnXMLUtil.addXMLLocation(multiInstanceDef, xtr);
        if (xtr.getAttributeValue(null, CUSTOM_ATTRIBUTE_MULTIINSTANCE_SEQUENTIAL) != null) {
            multiInstanceDef.setSequential(Boolean.valueOf(xtr.getAttributeValue(null, CUSTOM_ATTRIBUTE_MULTIINSTANCE_SEQUENTIAL)));
        }

        boolean readyWithMultiInstance = false;
        try {
            while (!readyWithMultiInstance && xtr.hasNext()) {
                xtr.next();
                if (xtr.isStartElement() && CUSTOM_ELEMENT_MULTIINSTANCE_CONDITION.equalsIgnoreCase(xtr.getLocalName())) {
                    multiInstanceDef.setCompletionCondition(xtr.getElementText());

                }  else if (xtr.isEndElement() && getElementName().equalsIgnoreCase(xtr.getLocalName())) {
                    readyWithMultiInstance = true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error parsing multi instance definition", e);
        }

        ((Activity) parentElement).setCustomLoopCharacteristics(multiInstanceDef);
    }
}
