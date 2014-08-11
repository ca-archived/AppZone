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
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.filefilter.AbstractFileFilter;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;

class DeployStrategyIOs extends DeployStrategy {

    private final FilePath mIpaFile;
    private final FilePath mPlistFile;

    public DeployStrategyIOs(final String server, final String id, final String tag,
            final boolean prependNameToTag, final FilePath ipaFile, final AbstractBuild build,
            final BuildListener listener) {
        super(server, "ios", id, tag, prependNameToTag, build, listener);
        mIpaFile = ipaFile;
        mPlistFile = findPlistFile();
    }

    private FilePath findPlistFile() {
        try {
            List<FilePath> plistFiles = FileListUtil.listFilesRecursively(
                    getBuild().getWorkspace(), new PlistFileFilter(mIpaFile));
            if (plistFiles.isEmpty()) {
                return null;
            } else if (plistFiles.size() > 1) {
                getLogger().println(TAG + "Error: Found multiple Info.plist files in *.app folders.");
                return null;
            }
            return plistFiles.get(0);
        } catch (Exception e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            getLogger().println(writer.toString());
        }
        return null;
    }

    @Override
    public List<Part> getParts() throws FileNotFoundException {
        List<Part> parts = super.getParts();
        parts.add(new FilePart("ipa", new FilePathPartSource(mIpaFile)));
        return parts;
    }

    @Override
    public String getVersion() {
        try {
            return getBundleVersionVersion();
        } catch (Exception e) {
            getLogger().println(TAG + "Error: " + e.getMessage());
        }
        return super.getVersion();
    }

    private String getBundleVersionVersion() throws Exception {
        if (mPlistFile == null) {
            getLogger().println(TAG + "No Info.plist file found. Aborting.");
            return null;
        }
        File tmpFile = null;
        FileOutputStream stream = null;
        try {
            tmpFile = File.createTempFile(getBuild().getId() + "-" + getBuild().getNumber(), "plist-name");
            stream = new FileOutputStream(tmpFile);
            mPlistFile.copyTo(stream);
            NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(tmpFile);

            String bundleShortVersionString = rootDict.objectForKey("CFBundleShortVersionString")
                    .toString();
            String bundleVersion = rootDict.objectForKey("CFBundleVersion").toString();
            return bundleShortVersionString + " (" + bundleVersion + ")";
        } finally {
            if (tmpFile != null)
                tmpFile.delete();
            if (stream != null)
                stream.close();
        }
    }

    @Override
    public String getDeployableName() {
        if (mPlistFile == null) {
            getLogger().println(TAG + "No Info.plist file found. Aborting.");
            return null;
        }
        File tmpFile = null;
        FileOutputStream stream = null;
        try {
            tmpFile = File.createTempFile(getBuild().getId() + "-" + getBuild().getNumber(), "plist-name");
            stream = new FileOutputStream(tmpFile);
            mPlistFile.copyTo(stream);
            NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(tmpFile);
            return rootDict.objectForKey("CFBundleDisplayName").toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (tmpFile != null)
                tmpFile.delete();
            if (stream != null)
                try {
                    stream.close();
                } catch (IOException e) {
                    getLogger().println(TAG + "Error: " + e.getMessage());
                }
        }
    }

    private static class PlistFileFilter extends AbstractFileFilter implements Serializable {
        private final FilePath mIpaFile;

        public PlistFileFilter(FilePath ipaFile) {
            mIpaFile = ipaFile;
        }

        @Override
        public boolean accept(final File file) {
            if (file.getAbsolutePath().endsWith(".app/Info.plist")) {
                Pattern pattern = Pattern
                        .compile(".*\\/([^\\.\\/]+)\\.app\\/Info.plist");
                Matcher matcher = pattern.matcher(file.getAbsolutePath());
                return matcher.matches()
                        && mIpaFile.getName().startsWith(matcher.group(1));
            } else {
                return false;
            }
        }
    }
}
