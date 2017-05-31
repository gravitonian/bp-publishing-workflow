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
import org.acme.bestpublishing.services.AlfrescoWorkflowUtilsService;
import org.acme.bestpublishing.services.BestPubUtilsService;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

    private final DateFormat publishingDateFormat = new SimpleDateFormat("dd/MM/YYYY");

    /**
     * BestPub utility services
     */
    private AlfrescoWorkflowUtilsService alfrescoWorkflowUtilsService;
    private BestPubUtilsService bestPubUtilsService;

    /**
     * Spring dependency injection
     */
    public void setAlfrescoWorkflowUtilsService(AlfrescoWorkflowUtilsService alfrescoWorkflowUtilsService) {
        this.alfrescoWorkflowUtilsService = alfrescoWorkflowUtilsService;
    }

    public void setBestPubUtilsService(BestPubUtilsService bestPubUtilsService) {
        this.bestPubUtilsService = bestPubUtilsService;
    }

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

        // Put together a list of chapter metadata, exluding the entry for book metadata
        List<Properties> chapterMetadataList = new ArrayList<>();
        for (Map.Entry<String, Properties> entry : allMetadata.entrySet()) {
            // Only add if it is not book metadata (i.e. {isbn} -> Properties), chapter metadata has keys like for
            // example {isbn-chapter-1} -> Properties
            if (entry.getKey().equalsIgnoreCase(isbn) == false) {
                chapterMetadataList.add(entry.getValue());
            }
        }

        // Go through chapter metadata and extract Chapter Title, then use it to
        // setup the complete chapter list for the book
        Set<String> chapterTitles = new LinkedHashSet<String>(); // Keeping the order
        for (Properties chapterMetadata : chapterMetadataList) {
            String chapterTitle = (String)chapterMetadata.get(
                    BestPubMetadataFileModel.CHAPTER_METADATA_TITLE_PROP_NAME);
            chapterTitles.add(chapterTitle);
            LOG.debug("Added chapter title [{}] for chapter number [{}] {}",
                    new Object[]{chapterTitle, chapterMetadata.get(
                            BestPubMetadataFileModel.CHAPTER_METADATA_NUMBER_PROP_NAME), processInfo});
        }
        setWorkflowVariable(exec, VAR_CHAPTER_LIST, chapterTitles, processInfo);
        if (chapterTitles.isEmpty()) {
            LOG.error("Chapter title list could not be setup, no chapter metadata was extracted {}", processInfo);
        }
        setWorkflowVariable(exec, VAR_BOOK_GENRE, allMetadata.get(isbn).get(
                BestPubMetadataFileModel.BOOK_METADATA_GENRE_PROP_NAME), processInfo);
        setWorkflowVariable(exec, VAR_CHAPTER_COUNT, chapterTitles.size(), processInfo);

        // Set the Publishing date to now as a 'backlist' book has already been published
        Date today = new Date();
        setWorkflowVariable(exec, VAR_PUBLISHING_DATE, publishingDateFormat.format(today), processInfo);
    }


}
