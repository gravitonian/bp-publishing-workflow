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

import org.acme.bestpublishing.model.BestPubMetadataFileModel;
import org.acme.bestpublishing.model.BestPubWorkflowModel;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;

/**
 * Called from workflow to execute the T2 service task.
 * It should set up a chapter title list based on passed in metadata.
 * This task will also setup Publishing Date to Now as it is a so called 'backlist' item
 * (book already published).
 *
 * @author martin.bergljung@marversolutions.org
 */
public class T2SetupChapterListJavaDelegate extends BestPubBaseJavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(T2SetupChapterListJavaDelegate.class);

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
        LOG.debug("T2 - Setting up chapter list {}", processInfo);

        // Get all the metadata attached to workflow instance
        Map<String, Properties> allMetadata = (HashMap<String, Properties>)exec.getVariable(VAR_ALL_METADATA);
        if (allMetadata.isEmpty()) {
            LOG.error("Metadata is not available, cannot setup chapter list {}", processInfo);
            return;
        }

        // Separate book metadata from individual chapter metadata
        Properties bookMetadata = null;
        List<Properties> chapterMetadataList = new ArrayList<>();
        for (Map.Entry<String, Properties> entry : allMetadata.entrySet()) {
            // Only add if it is not book metadata (i.e. {isbn} -> Properties), chapter metadata has keys like for
            // example {isbn_chapter_1} -> Properties
            if (entry.getKey().equalsIgnoreCase(isbn)) {
                bookMetadata = entry.getValue();
            } else {
                chapterMetadataList.add(entry.getValue());
            }
        }

        if (chapterMetadataList.isEmpty()) {
            LOG.error("Chapter list could not be setup, no chapter metadata was extracted {}", processInfo);
        }

        // Check that we got the same number of chapters as specified in book metadata
        String numberOfChaptersAsString = bookMetadata.getProperty(
                BestPubMetadataFileModel.BOOK_METADATA_NR_OF_CHAPTERS_PROP_NAME);
        int numberOfChapters = Integer.parseInt(numberOfChaptersAsString);
        if (numberOfChapters != chapterMetadataList.size()) {
            LOG.error("Book metadata specifies different number of chapters then was supplied " +
                    "[Expected={}][Supplied={}]{}", numberOfChapters, chapterMetadataList.size(), processInfo);
        }

        setWorkflowVariable(exec, BestPubWorkflowModel.VAR_BOOK_INFO, bookMetadata, processInfo);
        setWorkflowVariable(exec, BestPubWorkflowModel.VAR_CHAPTER_LIST, chapterMetadataList, processInfo);

        LOG.debug("Finished T2 - Setting up chapter list {}", processInfo);
    }
}
