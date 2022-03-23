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
package org.flowable.bpmn.model;

/**
 * @author Tijs Rademakers
 */
public class CustomMultiInstanceLoopCharacteristics extends BaseElement {

    protected boolean sequential;
    protected String completionCondition;

    public boolean isSequential() {
        return sequential;
    }

    public void setSequential(boolean sequential) {
        this.sequential = sequential;
    }

    public String getCompletionCondition() {
        return completionCondition;
    }

    public void setCompletionCondition(String completionCondition) {
        this.completionCondition = completionCondition;
    }

    @Override
    public CustomMultiInstanceLoopCharacteristics clone() {
        CustomMultiInstanceLoopCharacteristics clone = new CustomMultiInstanceLoopCharacteristics();
        clone.setValues(this);
        return clone;
    }

    public void setValues(CustomMultiInstanceLoopCharacteristics otherLoopCharacteristics) {
        setCompletionCondition(otherLoopCharacteristics.getCompletionCondition());
        setSequential(otherLoopCharacteristics.isSequential());
    }
}
