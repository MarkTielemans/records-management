/*
 * Copyright (C) 2005-2017 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.bm.dataload.rm.unfiled;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.dataload.RmBaseEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.session.SessionService;
import org.alfresco.rest.core.RestAPIFactory;
import org.alfresco.rest.rm.community.model.fileplancomponents.FilePlanComponent;
import org.alfresco.rest.rm.community.model.fileplancomponents.FilePlanComponentType;
import org.alfresco.rest.rm.community.requests.igCoreAPI.FilePlanComponentAPI;
import org.alfresco.utility.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prepare event for loading unfiled records
 *
 * @author Silviu Dinuta
 * @since 2.6
 *
 */
public class ScheduleUnfiledRecordLoaders extends RmBaseEventProcessor
{
    public static final String EVENT_NAME_LOAD_UNFILED_RECORDS = "loadUnfiledRecords";
    public static final String EVENT_NAME_SCHEDULE_LOADERS = "scheduleUnfiledRecordLoaders";
    public static final String EVENT_NAME_LOADING_COMPLETE = "loadingUnfiledRecordsComplete";

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RestAPIFactory restAPIFactory;

    private int maxActiveLoaders;
    private boolean uploadUnfiledRecords;
    private int unfiledRecordsNumber;
    private String unfiledRecordFolderPaths;
    private List<String> paths;
    private long loadCheckDelay;
    private String username;
    private String eventNameLoadUnfiledRecords = EVENT_NAME_LOAD_UNFILED_RECORDS;
    private String eventNameScheduleLoaders = EVENT_NAME_SCHEDULE_LOADERS;
    private String eventNameLoadingComplete = EVENT_NAME_LOADING_COMPLETE;
    private List<FolderData> unfiledRecordFoldersThatNeedRecords = null;
    private HashMap<FolderData, Integer> mapOfRecordsPerUnfiledRecordFolder = null;

    public int getMaxActiveLoaders()
    {
        return maxActiveLoaders;
    }

    public void setMaxActiveLoaders(int maxActiveLoaders)
    {
        this.maxActiveLoaders = maxActiveLoaders;
    }

    public boolean isUploadUnfiledRecords()
    {
        return uploadUnfiledRecords;
    }

    public void setUploadUnfiledRecords(boolean uploadUnfiledRecords)
    {
        this.uploadUnfiledRecords = uploadUnfiledRecords;
    }

    public int getUnfiledRecordsNumber()
    {
        return unfiledRecordsNumber;
    }

    public void setUnfiledRecordsNumber(int unfiledRecordsNumber)
    {
        this.unfiledRecordsNumber = unfiledRecordsNumber;
    }

    public String getUnfiledRecordFolderPaths()
    {
        return unfiledRecordFolderPaths;
    }

    public void setUnfiledRecordFolderPaths(String unfiledRecordFolderPaths)
    {
        this.unfiledRecordFolderPaths = unfiledRecordFolderPaths;
        if(isNotBlank(this.unfiledRecordFolderPaths))
        {
            paths = Arrays.asList(this.unfiledRecordFolderPaths.split(","));
        }
    }

    public long getLoadCheckDelay()
    {
        return loadCheckDelay;
    }

    public void setLoadCheckDelay(long loadCheckDelay)
    {
        this.loadCheckDelay = loadCheckDelay;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getEventNameLoadUnfiledRecords()
    {
        return eventNameLoadUnfiledRecords;
    }

    public void setEventNameLoadUnfiledRecords(String eventNameLoadUnfiledRecords)
    {
        this.eventNameLoadUnfiledRecords = eventNameLoadUnfiledRecords;
    }

    public String getEventNameScheduleLoaders()
    {
        return eventNameScheduleLoaders;
    }

    public void setEventNameScheduleLoaders(String eventNameScheduleLoaders)
    {
        this.eventNameScheduleLoaders = eventNameScheduleLoaders;
    }

    public String getEventNameLoadingComplete()
    {
        return eventNameLoadingComplete;
    }

    public void setEventNameLoadingComplete(String eventNameLoadingComplete)
    {
        this.eventNameLoadingComplete = eventNameLoadingComplete;
    }

    public List<String> getPaths()
    {
        return paths;
    }

    public List<FolderData> getUnfiledRecordFoldersThatNeedRecords()
    {
        return unfiledRecordFoldersThatNeedRecords;
    }

    public HashMap<FolderData, Integer> getMapOfRecordsPerUnfiledRecordFolder()
    {
        return mapOfRecordsPerUnfiledRecordFolder;
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        // Are there still sessions active?
        long sessionCount = sessionService.getActiveSessionsCount();
        int loaderSessionsToCreate = maxActiveLoaders - (int) sessionCount;
        List<Event> nextEvents = new ArrayList<Event>(maxActiveLoaders);

        // Do we actually need to do anything
        if (!isUploadUnfiledRecords())
        {
            return new EventResult("Uploading of Unfiled Records not wanted.", false);
        }
        if(unfiledRecordsNumber > 0)
        {
            //Prepare Records
            prepareUnfiledRecords(loaderSessionsToCreate, nextEvents);
        }

        // If there are no events, then we have finished
        String msg = null;
        if (loaderSessionsToCreate > 0 && nextEvents.size() == 0)
        {
            // There are no records to load even though there are sessions available
            unfiledRecordFoldersThatNeedRecords = null;
            mapOfRecordsPerUnfiledRecordFolder = null;
            Event nextEvent = new Event(eventNameLoadingComplete, null);
            nextEvents.add(nextEvent);
            msg = "Loading completed.  Raising 'done' event.";
        }
        else
        {
            // Reschedule self
            Event nextEvent = new Event(eventNameScheduleLoaders, System.currentTimeMillis() + loadCheckDelay, null);
            nextEvents.add(nextEvent);
            msg = "Raised further " + (nextEvents.size() - 1) + " events and rescheduled self.";
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }

        EventResult result = new EventResult(msg, nextEvents);
        return result;
    }

    private void calculateListOfEmptyFolders()
    {
        if(unfiledRecordFoldersThatNeedRecords == null)
        {
            unfiledRecordFoldersThatNeedRecords = new ArrayList<FolderData>();
            if(paths == null || paths.size() == 0)
            {
                unfiledRecordFoldersThatNeedRecords.addAll(initialiseFoldersToExistingStructure(UNFILED_CONTEXT));
            }
            else
            {
                LinkedHashSet<FolderData> unfiledFolderStructerFromExistentProvidedPaths = new LinkedHashSet<FolderData>();
                for(String path : paths)
                {
                    FolderData folder = fileFolderService.getFolder(UNFILED_CONTEXT, UNFILED_RECORD_CONTAINER_PATH + path);
                    if(folder != null)//if folder exists
                    {
                        unfiledFolderStructerFromExistentProvidedPaths.addAll(getUnfiledRecordFolders(folder));
                    }
                    else
                    {
                        try
                        {
                            folder = createFolder(path);
                            unfiledRecordFoldersThatNeedRecords.add(folder);
                        }
                        catch(Exception e)
                        {
                            // something went wrong on creating current path structure, not all required paths will be created
                        }
                    }
                }
                //add unfiled record folders from existent paths
                if(unfiledFolderStructerFromExistentProvidedPaths.size() > 0)
                {
                    unfiledRecordFoldersThatNeedRecords.addAll(unfiledFolderStructerFromExistentProvidedPaths);
                }
                // configured paths did not existed in db and something went wrong with creation for all of them, initialize to existing structure in this case
                if(unfiledRecordFoldersThatNeedRecords.size() == 0)
                {
                    unfiledRecordFoldersThatNeedRecords.addAll(initialiseFoldersToExistingStructure(UNFILED_CONTEXT));
                }
            }
           if(unfiledRecordFoldersThatNeedRecords.size() > 0)
           {
               mapOfRecordsPerUnfiledRecordFolder = distributeNumberOfRecords(unfiledRecordFoldersThatNeedRecords, unfiledRecordsNumber);
           }
        }
    }

    /**
     * Obtains all unfiled record folders underneath specified parent folder plus the parent folder
     *
     * @param parentFolder
     * @return all unfiled record folders underneath specified parent folder plus the parent folder
     */
    private Set<FolderData> getUnfiledRecordFolders(FolderData parentFolder)
    {
        LinkedHashSet<FolderData> result = new LinkedHashSet<FolderData>();
        int skip = 0;
        int limit = 100;
        List<FolderData> directChildren = new ArrayList<FolderData>();
        List<FolderData> childFolders = fileFolderService.getChildFolders(UNFILED_CONTEXT, parentFolder.getPath(), skip, limit);
        while(childFolders.size() > 0)
        {
            directChildren.addAll(childFolders);
            skip += limit;
            childFolders = fileFolderService.getChildFolders(UNFILED_CONTEXT, parentFolder.getPath(), skip, limit);
        }
        if(directChildren.size() > 0)
        {
            for(FolderData childFolder : directChildren)
            {
                result.addAll(getUnfiledRecordFolders(childFolder));
            }
        }
        result.add(parentFolder);
        return result;
    }

    private FolderData createFolder(String path) throws Exception
    {
        FilePlanComponentAPI api = restAPIFactory.getFilePlanComponentsAPI(new UserModel(getUsername(), getUsername()));
        List<String> pathElements = getPathElements(path);
        FolderData parentFolder = fileFolderService.getFolder(UNFILED_CONTEXT, UNFILED_RECORD_CONTAINER_PATH);
        for(String pathElement: pathElements)
        {
            FilePlanComponent filePlanComponent = api.getFilePlanComponent(parentFolder.getId());
            FolderData folder = fileFolderService.getFolder(UNFILED_CONTEXT, parentFolder.getPath() + "/" + pathElement);
            if(folder != null)
            {
                parentFolder = folder;
            }
            else
            {
                parentFolder = createFilePlanComponentWithFixedName(parentFolder, api, filePlanComponent, pathElement, FilePlanComponentType.UNFILED_RECORD_FOLDER_TYPE.toString(), parentFolder.getContext());
            }
        }
        return parentFolder;
    }

    private void prepareUnfiledRecords(int loaderSessionsToCreate, List<Event> nextEvents)
    {
        calculateListOfEmptyFolders();
        List<FolderData> emptyFolders = new ArrayList<FolderData>();
        emptyFolders.addAll(unfiledRecordFoldersThatNeedRecords);
        while (nextEvents.size() < loaderSessionsToCreate)
        {
            if(mapOfRecordsPerUnfiledRecordFolder == null || mapOfRecordsPerUnfiledRecordFolder.size() == 0)
            {
                break;
            }
            // Schedule a load for each folder
            for (FolderData emptyFolder : emptyFolders)
            {
                int recordsToCreate = mapOfRecordsPerUnfiledRecordFolder.get(emptyFolder) - (int) emptyFolder.getFileCount();
                if(recordsToCreate <= 0)
                {
                    unfiledRecordFoldersThatNeedRecords.remove(emptyFolder);
                    mapOfRecordsPerUnfiledRecordFolder.remove(emptyFolder);
                }
                else
                {
                    try
                    {
                        // Create a lock folder that has too many files and folders so that it won't be picked up
                        // by this process in subsequent trawls
                        String lockPath = emptyFolder.getPath() + "/locked";
                        FolderData lockFolder = new FolderData(
                                UUID.randomUUID().toString(),
                                emptyFolder.getContext(), lockPath,
                                Long.MAX_VALUE, Long.MAX_VALUE);
                        fileFolderService.createNewFolder(lockFolder);
                        // We locked this, so the load can be scheduled.
                        // The loader will remove the lock when it completes
                        DBObject loadData = BasicDBObjectBuilder.start()
                                .add(FIELD_CONTEXT, emptyFolder.getContext())
                                .add(FIELD_PATH, emptyFolder.getPath())
                                .add(FIELD_RECORDS_TO_CREATE, Integer.valueOf(recordsToCreate))
                                .add(FIELD_SITE_MANAGER, username)
                                .get();
                        Event loadEvent = new Event(eventNameLoadUnfiledRecords, loadData);
                        // Each load event must be associated with a session
                        String sessionId = sessionService.startSession(loadData);
                        loadEvent.setSessionId(sessionId);
                        // Add the event to the list
                        nextEvents.add(loadEvent);
                        unfiledRecordFoldersThatNeedRecords.remove(emptyFolder);
                        mapOfRecordsPerUnfiledRecordFolder.remove(emptyFolder);
                    }
                    catch (Exception e)
                    {
                        // The lock was already applied; find another
                        continue;
                    }
                }
                // Check if we have enough
                if (nextEvents.size() >= loaderSessionsToCreate)
                {
                    break;
                }
            }
        }
    }
}