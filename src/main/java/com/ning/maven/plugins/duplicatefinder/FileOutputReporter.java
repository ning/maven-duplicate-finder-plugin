/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.maven.plugins.duplicatefinder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileOutputReporter implements DuplicateFinderReporter {
	
	protected final Logger LOG = LoggerFactory.getLogger(this.getClass());
	private final String fileName;
	
	private static final int DUPLICATE_IDENTICAL=0;
	private static final int DUPLICATE_DIFFERENCE=0;
	
	public FileOutputReporter(String fileName) {
		this.fileName = fileName;
	}
	
	public void reportEqualConflicts(String projectName, String projectVersion,Map classEqualConflictsByArtifactNames,String type) throws MojoExecutionException {
		writeToFile(projectName,projectVersion,classEqualConflictsByArtifactNames,DUPLICATE_IDENTICAL, type);		
	}

	public void reportDifferentConflicts(String projectName, String projectVersion,Map classDifferentConflictsByArtifactNames, String type) throws MojoExecutionException {
		writeToFile(projectName,projectVersion,classDifferentConflictsByArtifactNames, DUPLICATE_DIFFERENCE, type);		
	}
	
	 /**
     * Prints the following message. 
     * 
     * [WARNING] Found duplicate (but equal) resources in [be.ecs:srv.customer.service1:0.0.1-SNAPSHOT,be.ecs:srv.customer.service2:0.0.1-SNAPSHOT] :
     * [WARNING]   META-INF/MANIFEST.MF
     * [WARNING]   be/ecs/duplicate_different_same.properties
	 * @param projectVersion 
	 * @param projectName 
     * 
     * 
     * 
     * @param conflictsByArtifactNames the Map of conflicts (Artifactnames, List of classes)
     * @param duplicateIdentical hint with the type of the conflict ("all equal" or "content different")
     * @param type type of conflict (class or resource)
	 * @throws MojoExecutionException 
     */
    private void writeToFile(String projectName, String projectVersion, Map conflictsByArtifactNames, int duplicateIdentical, String type) throws MojoExecutionException
    {
		try {
			//PrintWriter writer = new PrintWriter(fileName,CHARSET);
			PrintWriter writer = new PrintWriter(new FileOutputStream(new File(fileName), true)); 

	        for (Iterator conflictIt = conflictsByArtifactNames.entrySet().iterator(); conflictIt.hasNext();) {
	            Map.Entry entry         = (Map.Entry)conflictIt.next();
	            String    artifactNames = (String)entry.getKey();
	            List      classNames    = (List)entry.getValue();
	            
	            for (Iterator classNameIt = classNames.iterator(); classNameIt.hasNext();) {
	            	writer.println(projectName + "," + projectVersion + "," + classNameIt.next() + "," + type + "," + duplicateIdentical + "," + artifactNames );
	            }
	        }

	        writer.close();
		} catch (FileNotFoundException e) {
			throw new MojoExecutionException("Error writing to output file.",e);
		}
        
    }	

}
