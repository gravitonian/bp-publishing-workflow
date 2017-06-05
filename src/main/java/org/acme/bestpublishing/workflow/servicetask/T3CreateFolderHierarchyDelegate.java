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

import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;

/**
 * Called from a workflow to execute the T3 service task for backlist book (already published book).
 * Creates a new ISBN folder under /Company Home/Sites/book-management/documentLibrary/2017/{isbn}
 * with basic book metadata.
 * Then goes through all the chapters setup in T2 (automatic from ZIP) and creates corresponding number of
 * chapter folders with basic chapter metadata.
 *
 * @author martin.bergljung@marversolutions.org
 */
public class T3CreateFolderHierarchyDelegate extends BestPubBaseJavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(T3CreateFolderHierarchyDelegate.class);

    /**
     * Interface Implementation
     */

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    public void execute(DelegateExecution exec) throws Exception {
        String isbn = getISBN(exec);
        if (StringUtils.isBlank(isbn)) {
            return;
        }

        String processInfo = getProcInfo(exec, isbn);

        LOG.debug("T3 - Creating chapter folder hierarchy {}", processInfo);

        // Get book info and chapter list metadata
        Properties bookInfo = (Properties) exec.getVariable(VAR_BOOK_INFO);
        List<Properties> chapterList = (List<Properties>) exec.getVariable(VAR_CHAPTER_LIST);

        // Create the new folder hierarchy for the ISBN
        NodeRef isbnFolderNodeRef = bestPubUtilsService.createChapterFolders(isbn, bookInfo, chapterList, processInfo);

        // Store away the ISBN Folder node ref so it can be easily used in Task Forms
        setWorkflowVariable(exec, VAR_ISBN_FOLDER_NODEREF, isbnFolderNodeRef.toString(), processInfo);

        // Tell the workflow that there is now a folder hierarchy where content can be uploaded
        setWorkflowVariable(exec, VAR_CHAPTER_FOLDER_HIERARCHY_EXISTS, true, processInfo);

        LOG.debug("Finished T3 - Creating chapter folder hierarchy {}", processInfo);
    }
}