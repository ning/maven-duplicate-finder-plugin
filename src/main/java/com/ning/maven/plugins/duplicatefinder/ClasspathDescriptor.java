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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;

public class ClasspathDescriptor
{
    private static final Pattern[] DEFAULT_IGNORED_RESOURCES = { Pattern.compile("(META-INF/)?ASL2\\.0(\\.TXT)?"),
                                                                 Pattern.compile("META-INF/DEPENDENCIES(\\.TXT)?"),
                                                                 Pattern.compile("META-INF/DISCLAIMER(\\.TXT)?"),
                                                                 Pattern.compile("(META-INF/)?[A-Z_-]*LICENSE.*"),
                                                                 Pattern.compile("META-INF/MANIFEST\\.MF"),
                                                                 Pattern.compile("META-INF/INDEX\\.LIST"),
                                                                 Pattern.compile("META-INF/MAVEN/.*"),
                                                                 Pattern.compile("META-INF/PLEXUS/.*"),
                                                                 Pattern.compile("META-INF/SERVICES/.*"),
                                                                 Pattern.compile("(META-INF/)?NOTICE(\\.TXT)?"),
                                                                 Pattern.compile("META-INF/README"),
                                                                 Pattern.compile("OSGI-INF/.*"),
                                                                 Pattern.compile("README(\\.TXT)?"),
                                                                 Pattern.compile(".*PACKAGE\\.HTML"),
                                                                 Pattern.compile(".*OVERVIEW\\.HTML"),
                                                                 Pattern.compile("META-INF/SPRING\\.HANDLERS"),
                                                                 Pattern.compile("META-INF/SPRING\\.SCHEMAS"),
                                                                 Pattern.compile("META-INF/SPRING\\.TOOLING")};
    
    private static final Set IGNORED_LOCAL_DIRECTORIES = new HashSet();

    private static final Map CACHED_BY_ELEMENT = new HashMap();

    static {
        IGNORED_LOCAL_DIRECTORIES.add(".GIT");
        IGNORED_LOCAL_DIRECTORIES.add(".SVN");
        IGNORED_LOCAL_DIRECTORIES.add(".HG");
        IGNORED_LOCAL_DIRECTORIES.add(".BZR");
    }

    // TreeMap<String, File>
    private Map classesWithElements   = new TreeMap();
    
    // TreeMap<String, File>
    private Map resourcesWithElements = new TreeMap();
    
    private boolean useDefaultResourceIgnoreList = true;

    private Pattern [] ignoredResourcesPatterns = null;

    public boolean isUseDefaultResourceIgnoreList()
    {
        return useDefaultResourceIgnoreList;
    }

    public void setUseDefaultResourceIgnoreList(boolean useDefaultResourceIgnoreList)
    {
        this.useDefaultResourceIgnoreList = useDefaultResourceIgnoreList;
    }

    public void setIgnoredResources(final String [] ignoredResources) throws MojoExecutionException
    {
        if (ignoredResources != null) {
            ignoredResourcesPatterns = new Pattern [ignoredResources.length];

            try {
                for (int i = 0 ; i < ignoredResources.length; i++) {
                    ignoredResourcesPatterns[i] = Pattern.compile(ignoredResources[i].toUpperCase());
                }
            } catch (PatternSyntaxException pse) {
                throw new MojoExecutionException("Error compiling resourceIgnore pattern: " + pse.getMessage());
            }
        }
    }

    public void add(File element) throws IOException
    {
        if (!element.exists()) {
            throw new FileNotFoundException("Path " + element + " doesn't exist");
        }
        if (element.isDirectory()) {
            addDirectory(element);
        }
        else {
            addArchive(element);
        }
    }

    public Set getClasss()
    {
        return Collections.unmodifiableSet(classesWithElements.keySet());
    }

    public Set getResources()
    {
        return Collections.unmodifiableSet(resourcesWithElements.keySet());
    }

    public Set getElementsHavingClass(String className)
    {
        Set elements = (Set)classesWithElements.get(className);

        return elements == null ? null : Collections.unmodifiableSet(elements);
    }

    public Set getElementsHavingResource(String resource)
    {
        Set elements = (Set)resourcesWithElements.get(resource);

        return elements == null ? null : Collections.unmodifiableSet(elements);
    }

    private void addDirectory(File element)
    {
        addDirectory(element, null, element);
    }

    private void addDirectory(File element, String parentPackageName, File directory)
    {
        if (addCached(element)) {
            return;
        }

        List classes = new ArrayList();
        List resources = new ArrayList();
        File[] files    = directory.listFiles();
        String pckgName = (element.equals(directory) ? null : (parentPackageName == null ? "" : parentPackageName + ".") + directory.getName());

        if ((files != null) && (files.length > 0)) {
            for (int idx = 0; idx < files.length; idx++) {
                if (files[idx].isDirectory() && !IGNORED_LOCAL_DIRECTORIES.contains(files[idx].getName().toUpperCase())) {
                    addDirectory(element, pckgName, files[idx]);
                }
                else if (files[idx].isFile()) {
                    if ("class".equals(FilenameUtils.getExtension(files[idx].getName()))) {
                        String className = (pckgName == null ? "" : pckgName + ".") + FilenameUtils.getBaseName(files[idx].getName());

                        classes.add(className);
                        addClass(className, element);
                    }
                    else {
                        String resourcePath = (pckgName == null ? "" : pckgName.replace('.', '/') + "/")  + files[idx].getName();

                        resources.add(resourcePath);
                        addResource(resourcePath, element);
                    }
                }
            }
        }

        CACHED_BY_ELEMENT.put(element, new Cached(classes, resources));
    }

    private void addArchive(File element) throws IOException
    {
        if (addCached(element)) {
            return;
        }

        List classes = new ArrayList();
        List resources = new ArrayList();
        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(element);
            Enumeration entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (!entry.isDirectory()) {
                    String name = entry.getName();

                    if ("class".equals(FilenameUtils.getExtension(name))) {
                        String className = FilenameUtils.removeExtension(name).replace('/', '.').replace('\\', '.');

                        classes.add(className);
                        addClass(className, element);
                    }
                    else {
                        String resourcePath = name.replace('\\', File.separatorChar);

                        resources.add(resourcePath);
                        addResource(resourcePath, element);
                    }
                }
            }

            CACHED_BY_ELEMENT.put(element, new Cached(classes, resources));
        }
        finally {
            // IOUtils has no closeQuietly for Closable in the 1.x series.
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    // ignored
                }
            }
        }
    }

    private void addClass(String className, File element)
    {
        if (className.indexOf('$') < 0) {
            Set elements = (Set)classesWithElements.get(className);

            if (elements == null) {
                elements = new HashSet();
                classesWithElements.put(className, elements);
            }
            elements.add(element);
        }
    }

    private void addResource(String path, File element)
    {
        if (!ignore(path)) {
            Set elements = (Set)resourcesWithElements.get(path);

            if (elements == null) {
                elements = new HashSet();
                resourcesWithElements.put(path, elements);
            }
            elements.add(element);
        }
    }

    private boolean ignore(String path)
    {
        final String uppercasedPath = path.toUpperCase().replace(File.separatorChar, '/');

        // Unless it has been turned off...
        if (useDefaultResourceIgnoreList) {
            //  check whether the path is in the list of default ignores
            for (int idx = 0; idx < DEFAULT_IGNORED_RESOURCES.length; idx++) {
                if (DEFAULT_IGNORED_RESOURCES[idx].matcher(uppercasedPath).matches()) {
                    return true;
                }
            }
        }

        // check whether there is an user supplied ignore pattern.
        if (ignoredResourcesPatterns != null) {
            for (int idx = 0; idx < ignoredResourcesPatterns.length; idx++) {
                if (ignoredResourcesPatterns[idx].matcher(uppercasedPath).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean addCached(File element)
    {
        Cached cached = (Cached) CACHED_BY_ELEMENT.get(element);

        if (cached == null) {
            return false;
        }

        Iterator cachedClasses = cached.getClasses().iterator();
        Iterator cachedResources = cached.getResources().iterator();

        while (cachedClasses.hasNext()) {
            addClass((String) cachedClasses.next(), element);
        }

        while (cachedResources.hasNext()) {
            addResource((String) cachedResources.next(), element);
        }

        return true;
    }

    private static class Cached
    {
        private final List classes;
        private final List resources;

        private Cached(List classes, List resources)
        {
            this.classes = classes;
            this.resources = resources;
        }

        public List getClasses() {
            return classes;
        }

        public List getResources() {
            return resources;
        }
    }
}
