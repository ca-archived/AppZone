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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.lang.StringUtils;

abstract class DeployStrategy {
    public static final String TAG = "[AppZone] ";
    private final String mServer;
    private String mApiKey;
    private final String mType;
    private final String mId;
    private final String mTag;
    private final boolean mPrependNameToTag;
    private final AbstractBuild mBuild;
    private final BuildListener mListener;

    private String mUrl;
    private String mChangeLog;

    public DeployStrategy(final String server, final String type, final String id,
                          final String tag, final boolean prependNameToTag, final AbstractBuild build,
                          final BuildListener listener) {
        mServer = server;
        mType = type;
        mId = id;
        mTag = tag;
        mListener = listener;
        mPrependNameToTag = prependNameToTag;
        mBuild = build;
    }

    public void setChangeLog(final String changeLog) {
        mChangeLog = changeLog;
    }

    public String getUrl() {
        if (mUrl == null) {
            mUrl = createAppUrl(mServer, mType, mId, mTag);
        }
        return mUrl;
    }

    protected AbstractBuild getBuild() {
        return mBuild;
    }

    protected BuildListener getListener() {
        return mListener;
    }

    protected PrintStream getLogger() {
        return mListener.getLogger();
    }

    public String getVersion() {
        File versionFile = new File(mBuild.getWorkspace().getRemote() + "/VERSION");

        if (versionFile.exists()) {
            FileInputStream stream = null;
            DataInputStream in = null;
            InputStreamReader reader = null;
            BufferedReader br = null;
            try {
                stream = new FileInputStream(versionFile);
                in = new DataInputStream(stream);
                reader = new InputStreamReader(in);
                br = new BufferedReader(reader);
                return br.readLine();
            } catch (Exception e) {
                getLogger().println(TAG + "Error: " + e.getMessage());
            } finally {
                try {
                    if (stream != null)
                        stream.close();
                    if (br != null)
                        br.close();
                    if (reader != null)
                        reader.close();
                    if (in != null)
                        in.close();
                } catch (IOException e) {
                    getLogger().println(TAG + "Error: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private String createAppUrl(final String server, final String type, final String id,
                                String tag) {
        StringBuilder url = new StringBuilder(server);
        if (!server.endsWith("/")) {
            url.append("/");
        }
        url.append("app/" + id + "/" + type);

        tag = tag != null ? tag.trim() : "";

        if (mPrependNameToTag || !tag.isEmpty()) {
            url.append("/");

            if (mPrependNameToTag) {
                url.append(getDeployableName());
                if (!tag.isEmpty()) {
                    url.append("-");
                }
            }
            url.append(tag);
        }
        return url.toString();
    }

    public List<Part> getParts() throws FileNotFoundException {
        List<Part> list = new LinkedList<Part>();
        list.add(new StringPart("version", getVersion()));
        if (mChangeLog != null && mChangeLog.length() > 0) {
            list.add(new StringPart("changelog", mChangeLog, "UTF-8"));
        }
        if (!StringUtils.isEmpty(mApiKey)) {
            list.add(new StringPart("api_key", mApiKey, "UTF-8"));
        }
        return list;
    }

    public void setApiKey(String apiKey) {
        mApiKey = apiKey;
    }

    public abstract String getDeployableName();


    public static class FilePathPartSource implements PartSource {
        private final FilePath mFile;

        public FilePathPartSource(FilePath file) {
            mFile = file;
        }

        public long getLength() {
            try {
                return mFile.length();
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        }

        public String getFileName() {
            return mFile.getName();
        }

        public InputStream createInputStream() throws IOException {
            return mFile.read();
        }
    }
}
