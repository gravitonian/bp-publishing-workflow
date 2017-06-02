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

import org.acme.bestpublishing.model.BestPubContentModel;
import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.acme.bestpublishing.constants.BestPubConstants.INCOMING_CONTENT_FOLDER_PATH;
import static org.acme.bestpublishing.model.BestPubWorkflowModel.VAR_CONTENT_FOUND;

/**
 * Called from a workflow to execute the T4 service task looking for content from the Production department.
 * Look for a content folder '/Company Home/Data Dictionary/BestPub/Incoming/Content/{ISBN}'
 * with property 'ingestionStatus' set to 'complete'. If found set process variable
 * 'contentFound' to 'true'.
 *
 * @author martin.bergljung@marversolutions.org
 * @version 1.0
 */
public class T4CheckForContentDelegate extends BestPubBaseJavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(T4CheckForContentDelegate.class);

    /**
     * Interface implementation
     */

    @Override
    public Logger getLog() { return LOG; }

    @Override
    public void execute(DelegateExecution exec) throws Exception {
        String isbn = getISBN(exec);
        if (StringUtils.isBlank(isbn)) {
            return;
        }

        String processInfo = getProcInfo(exec, isbn);

        LOG.debug("T4 - Check for content {}", processInfo);

        NodeRef isbnFolderNodeRef = alfrescoRepoUtilsService.getNodeByDisplayPath(
                INCOMING_CONTENT_FOLDER_PATH + "/" + isbn);
        if (isbnFolderNodeRef != null && getServiceRegistry().getNodeService().getProperty(
                isbnFolderNodeRef, BestPubContentModel.BookFolderType.Prop.INGESTION_STATUS).equals(
                        BestPubContentModel.IngestionStatus.COMPLETE.toString())) {
            setWorkflowVariable(exec, VAR_CONTENT_FOUND, true, processInfo);
        } else {
            setWorkflowVariable(exec, VAR_CONTENT_FOUND, false, processInfo);
        }

        LOG.debug("Finished T4 - Check for content {}", processInfo);
    }
}
