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
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.acme.bestpublishing.constants.BestPubConstants.*;
import static org.acme.bestpublishing.model.BestPubMetadataFileModel.*;
import static org.acme.bestpublishing.model.BestPubWorkflowModel.*;
import static org.acme.bestpublishing.model.BestPubContentModel.*;

/**
 * Called from a workflow to execute the T3 service task for backlist book (already published book).
 * Creates a new ISBN folder under /Company Home/Sites/book-management/documentLibrary/2017/{isbn}
 * with basic book metadata.
 * Then goes through all the chapters setup in T2 (automatic from ZIP) and creates corresponding number of
 * chapter folders with basic chapter metadata.
 * Also creates the Artworks, Styles, and Supplementary folders.
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
        //
        // Start by creating the top ISBN folder
        NodeRef publishingYearNodeRef = bestPubUtilsService.getBaseFolderForBooks();
        NodeRef isbnFolderNodeRef = getServiceRegistry().getFileFolderService().create(
                publishingYearNodeRef, isbn, BookFolderType.QNAME).getNodeRef();
        LOG.debug("Created ISBN folder {} {}",
                alfrescoRepoUtilsService.getDisplayPathForNode(isbnFolderNodeRef), processInfo);

        // Set up the Book Info Aspect so it can be set on folders
        Map<QName, Serializable> bookInfoAspectProps = new HashMap<>();
        bookInfoAspectProps.put(BookInfoAspect.Prop.ISBN, isbn);
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_TITLE, bookInfo.getProperty(BOOK_METADATA_TITLE_PROP_NAME));
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_GENRE_NAME, bookInfo.getProperty(BOOK_METADATA_GENRE_PROP_NAME));
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_AUTHORS_NAME, bookInfo.getProperty(BOOK_METADATA_AUTHORS_PROP_NAME));
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_NUMBER_OF_CHAPTERS, bookInfo.getProperty(BOOK_METADATA_NR_OF_CHAPTERS_PROP_NAME));
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_NUMBER_OF_PAGES, bookInfo.getProperty(BOOK_METADATA_NR_OF_PAGES_PROP_NAME));
        BookMetadataStatus bookMetadataStatus = BookMetadataStatus.COMPLETED;

        // Now create all the chapter sub-folders under the new ISBN folder with chapter metadata
        for (Properties chapterInfo: chapterList) {
            String chapterFolderName = bestPubUtilsService.getChapterFolderName(
                    Integer.parseInt(chapterInfo.getProperty(CHAPTER_METADATA_NUMBER_PROP_NAME)));
            FileInfo chapterFileInfo = getServiceRegistry().getFileFolderService().create(
                    isbnFolderNodeRef, chapterFolderName, ChapterFolderType.QNAME);

            // Set up chapter metadata status
            ChapterMetadataStatus chapterMetadataStatus = ChapterMetadataStatus.COMPLETED;
            String chapterNumber = chapterInfo.getProperty(CHAPTER_METADATA_NUMBER_PROP_NAME);
            String chapterTitle = chapterInfo.getProperty(CHAPTER_METADATA_TITLE_PROP_NAME);
            String chapterAuthor = chapterInfo.getProperty(CHAPTER_METADATA_AUTHOR_PROP_NAME);
            if (StringUtils.isBlank(chapterNumber) ||
                    StringUtils.isBlank(chapterTitle) ||
                    StringUtils.isBlank(chapterAuthor)) {
                chapterMetadataStatus = ChapterMetadataStatus.MISSING;
                bookMetadataStatus = BookMetadataStatus.PARTIAL;
            }

            // Set aspects on the new chapter folder
            Map<QName, Serializable> chapterMetadataAspectProps = new HashMap<>();
            chapterMetadataAspectProps.put(ChapterInfoAspect.Prop.CHAPTER_NUMBER, chapterNumber);
            chapterMetadataAspectProps.put(ChapterInfoAspect.Prop.CHAPTER_TITLE, chapterTitle);
            chapterMetadataAspectProps.put(ChapterInfoAspect.Prop.CHAPTER_AUTHOR_NAME, chapterAuthor);
            chapterMetadataAspectProps.put(ChapterInfoAspect.Prop.CHAPTER_METADATA_STATUS, chapterMetadataStatus.toString());
            getServiceRegistry().getNodeService().addAspect(chapterFileInfo.getNodeRef(), BookInfoAspect.QNAME, bookInfoAspectProps);
            getServiceRegistry().getNodeService().addAspect(chapterFileInfo.getNodeRef(), ChapterInfoAspect.QNAME, chapterMetadataAspectProps);

            LOG.debug("Created chapter folder {} [chapterTitle={}] {}",
                    new Object[]{alfrescoRepoUtilsService.getDisplayPathForNode(chapterFileInfo.getNodeRef()),
                            chapterInfo.get(CHAPTER_METADATA_TITLE_PROP_NAME), processInfo});
        }

        // Set up the new /Company Home/Sites/book-management/documentLibrary/<year>/<isbn> folder with Book Info Aspect,
        // including metadata status for the whole book
        bookInfoAspectProps.put(BookInfoAspect.Prop.BOOK_METADATA_STATUS, bookMetadataStatus.toString());
        getServiceRegistry().getNodeService().addAspect(isbnFolderNodeRef, BookInfoAspect.QNAME, bookInfoAspectProps);

        // Create the other extra folders
        createExtraFolder(isbnFolderNodeRef, ARTWORK_FOLDER_NAME, bookInfoAspectProps, processInfo);
        createExtraFolder(isbnFolderNodeRef, SUPPLEMENTARY_FOLDER_NAME, bookInfoAspectProps, processInfo);
        createExtraFolder(isbnFolderNodeRef, STYLES_FOLDER_NAME, bookInfoAspectProps, processInfo);

        // Store away the ISBN Folder node ref so it can be easily used in Task Forms
        setWorkflowVariable(exec, VAR_ISBN_FOLDER_NODEREF, isbnFolderNodeRef.toString(), processInfo);

        // Tell the workflow that there is now a folder hierarchy where content can be uploaded
        setWorkflowVariable(exec, VAR_CHAPTER_FOLDER_HIERARCHY_EXISTS, true, processInfo);

        LOG.debug("Finished T3 - Creating chapter folder hierarchy {}", processInfo);
    }

    /**
     * Helper method to create some non-chapter folders
     *
     * @param isbnFolderNodeRef the ISBN folder node ref where the folder should be created
     * @param folderName the name of the new folder
     * @param bookInfoAspectProps the book info aspect properties
     * @param processInfo process information for logging purpose
     */
    private void createExtraFolder(NodeRef isbnFolderNodeRef, String folderName,
                                   Map<QName, Serializable> bookInfoAspectProps, String processInfo) {
        // Create the folder as standard general folder type
        FileInfo folderFileInfo = getServiceRegistry().getFileFolderService().create(
                isbnFolderNodeRef, folderName, ContentModel.TYPE_FOLDER);
        LOG.debug("Created {} folder {}", alfrescoRepoUtilsService.getDisplayPathForNode(
                folderFileInfo.getNodeRef()), processInfo);

        // Add basic book info metadata to folder
        getServiceRegistry().getNodeService().addAspect(
                folderFileInfo.getNodeRef(), BookInfoAspect.QNAME, bookInfoAspectProps);
    }
}