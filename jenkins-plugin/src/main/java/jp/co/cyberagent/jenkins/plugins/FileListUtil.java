/*
 * Copyright (C) 2013 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.jenkins.plugins;

import hudson.FilePath;

import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileListUtil {
    private FileListUtil() {}

    public static List<FilePath> listFilesRecursively(FilePath folder, FileFilter filter)
            throws IOException, InterruptedException {
        ArrayList<FilePath> foundFiles = new ArrayList<FilePath>();
        listFilesRecursively(folder, filter, foundFiles);
        return foundFiles;
    }

    private static void listFilesRecursively(FilePath folder, FileFilter filter, List<FilePath> files)
            throws IOException, InterruptedException {
        files.addAll(folder.list(filter));
        List<FilePath> folders = folder.listDirectories();
        for(FilePath subFolder: folders) {
            // No need to scan through .svn and .git
            // We ignore all . folders, because those most likely do not contain what we search for
            if (!subFolder.getName().startsWith(".")) {
                listFilesRecursively(subFolder, filter, files);
            }
        }
    }
}
