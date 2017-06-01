/*
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.acme.bestpublishing.workflow.servicetask;

import org.acme.bestpublishing.services.AlfrescoWorkflowUtilsService;
import org.acme.bestpublishing.services.BestPubUtilsService;
import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.repo.workflow.activiti.BaseJavaDelegate;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import static org.acme.bestpublishing.model.BestPubWorkflowModel.VAR_RELATED_ISBN;

/**
 * Base Java Delegate class for Service Task Java Delegate implementations.
 * Contains a method for setting workflow variable that will always log this to the system log.
 *
 * @author martin.bergljung@marversolutions.org
 */
public abstract class BestPubBaseJavaDelegate extends BaseJavaDelegate {

    /**
     * BestPub utility services
     */
    protected AlfrescoWorkflowUtilsService alfrescoWorkflowUtilsService;
    protected BestPubUtilsService bestPubUtilsService;

    /**
     * Spring dependency injection
     */
    public void setAlfrescoWorkflowUtilsService(AlfrescoWorkflowUtilsService alfrescoWorkflowUtilsService) {
        this.alfrescoWorkflowUtilsService = alfrescoWorkflowUtilsService;
    }

    public void setBestPubUtilsService(BestPubUtilsService bestPubUtilsService) {
        this.bestPubUtilsService = bestPubUtilsService;
    }

    public abstract Logger getLog();

    protected void setWorkflowVariable(DelegateExecution exec, String name, Object value, String processInfo) {
        exec.setVariable(name, value);
        getLog().debug("Setting workflow variable [{}={}] {}", new Object[]{name, value, processInfo});
    }

    /**
     * Compile a string of process information such as workflow instance ID and related ISBN, used for logging
     *
     * @param exec
     * @param isbn
     * @return
     */
    protected String getProcInfo(DelegateExecution exec, String isbn) {
        return String.format("[ISBN=%s][definition=%s][instance=%s]",
                new Object[]{isbn, exec.getProcessDefinitionId(), exec.getProcessInstanceId()});
    }

    /**
     * Return's ISBN number this that workflow instance is associated with
     *
     * @param exec the execution context to look in
     * @return the ISBN number, or null if not found
     */
    protected String getISBN(DelegateExecution exec) {
        String isbn = (String) exec.getVariable(VAR_RELATED_ISBN);
        if (StringUtils.isBlank(isbn)) {
            getLog().error("Process variable {} is not set, cannot proceed with service task [definition={}][instance={}]",
                    new Object[]{VAR_RELATED_ISBN, exec.getProcessDefinitionId(), exec.getProcessInstanceId()});
            return null;
        }

        return isbn;
    }
}
