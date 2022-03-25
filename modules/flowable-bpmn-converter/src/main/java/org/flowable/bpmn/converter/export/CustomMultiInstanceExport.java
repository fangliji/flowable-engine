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
package org.flowable.bpmn.converter.export;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.converter.util.BpmnXMLUtil;
import org.flowable.bpmn.model.*;

import javax.xml.stream.XMLStreamWriter;
import java.util.List;
import java.util.Map;

public class CustomMultiInstanceExport implements BpmnXMLConstants {

    public static void writeMultiInstance(Activity activity, BpmnModel model, XMLStreamWriter xtw) throws Exception {
        if (activity.getCustomLoopCharacteristics() != null) {
            CustomMultiInstanceLoopCharacteristics multiInstanceObject = activity.getCustomLoopCharacteristics();

            boolean didWriteExtensionStartElement = false;
            
            if (StringUtils.isNotEmpty(multiInstanceObject.getCompletionCondition())) {
                xtw.writeStartElement(CUSTOM_ELEMENT_MULTIINSTANCE);
                BpmnXMLUtil.writeDefaultAttribute(CUSTOM_ATTRIBUTE_MULTIINSTANCE_SEQUENTIAL, String.valueOf(multiInstanceObject.isSequential()).toLowerCase(), xtw);
            	// check for other custom extension elements
                Map<String, List<ExtensionElement>> extensions = multiInstanceObject.getExtensionElements();
                if (!extensions.isEmpty()) {
                    didWriteExtensionStartElement = BpmnXMLUtil.writeExtensionElements(multiInstanceObject, didWriteExtensionStartElement, model.getNamespaces(), xtw);
                }
                
                // end extensions element
                if (didWriteExtensionStartElement) {
                    xtw.writeEndElement();
                }

                if (StringUtils.isNotEmpty(multiInstanceObject.getCompletionCondition())) {
                    xtw.writeStartElement(CUSTOM_ELEMENT_MULTIINSTANCE_CONDITION);
                    xtw.writeCharacters(multiInstanceObject.getCompletionCondition());
                    xtw.writeEndElement();
                }
                // end multi-instance element
                xtw.writeEndElement();
            }
        }
    }
}
