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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class AndroidAppZonePublisher extends Notifier {
    public static final String DEFAULT_APPSERVER = "https://appzone.xxxx.com/api/";

    private static final String TAG = "[AppZone] ";

    private final String id;
    private final String tag;

    private final boolean prependNameToTag;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public AndroidAppZonePublisher(final String id, final String tag, final boolean prependNameToTag) {
        this.id = id;
        this.tag = tag;
        this.prependNameToTag = prependNameToTag;
    }

    public String getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public boolean getPrependNameToTag() {
        return prependNameToTag;
    }

    public FormValidation doCheckId(@QueryParameter
                                    final String value)
            throws IOException, ServletException {
        if (value.length() == 0) {
            return FormValidation.error("ID needs to be set.");
        }
        return FormValidation.ok();
    }

    @Override
    public boolean perform(final AbstractBuild build, final Launcher launcher,
                           final BuildListener listener) {
        StringBuilder changeLog = new StringBuilder();
        for (Object changeObject : build.getChangeSet().getItems()) {
            ChangeLogSet.Entry change = (ChangeLogSet.Entry) changeObject;
            if (changeLog.length() > 0) {
                changeLog.append("\n");
            }
            changeLog.append(change.getMsg() + " (" + change.getAuthor().getDisplayName() + ")");
        }
        String server = getDescriptor().getServer();
        if (server == null || server.length() == 0) {
            listener.getLogger().println(TAG +
                    "AppZone server not set. Please set in global config! Aborting.");
            return false;
        }

        List<FilePath> files = getPossibleAppFiles(build, listener);
        if (files.isEmpty()) {
            listener.getLogger().println(TAG + "No file to publish found. Skip.");
            return true;
        }
        Iterator<FilePath> fileIterator = files.iterator();
        while (fileIterator.hasNext()) {
            try {
                FilePath file = fileIterator.next();
                String fileName = file.getName();
                DeployStrategy deploy;
                listener.getLogger().println(TAG + "File: " + fileName);
                if (fileName.endsWith(".apk")) {
                    deploy = new DeployStrategyAndroid(server, id, tag, prependNameToTag, file,
                            build,
                            listener);
                } else if (fileName.endsWith(".ipa")) {
                    deploy = new DeployStrategyIOs(server, id, tag, prependNameToTag, file, build,
                            listener);
                } else {
                    return false;
                }
                deploy.setApiKey(getDescriptor().getApikey());
                deploy.setChangeLog(changeLog.toString());
                listener.getLogger().println(TAG + "Version: " + deploy.getVersion());
                listener.getLogger().println(TAG + "Publishing to: " + deploy.getUrl());

                setUpSsl();
                HttpClient httpclient = new HttpClient();
                PostMethod filePost = new PostMethod(deploy.getUrl());
                List<Part> parts = deploy.getParts();
                filePost.setRequestEntity(
                        new MultipartRequestEntity(parts.toArray(new Part[parts.size()]),
                                filePost.getParams()));
                httpclient.executeMethod(filePost);
                int statusCode = filePost.getStatusCode();
                if (statusCode < 200 || statusCode > 299) {
                    String body = filePost.getResponseBodyAsString();
                    listener.getLogger().println(TAG + "Response (" + statusCode + "):" + body);
                    return false;
                }
            } catch (IOException e) {
                listener.getLogger().print(e.getMessage());
                return false;
            }
        }
        return true;
    }

    private void setUpSsl() {
        // TODO maybe have setting or given certificate to check with.
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[0], new TrustManager[]{
                    new DefaultTrustManager()
            }, new SecureRandom());
            SSLContext.setDefault(ctx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<FilePath> getPossibleAppFiles(final AbstractBuild build,
                                               final BuildListener listener) {
        try {
            List<FilePath> files = FileListUtil.listFilesRecursively(
                    build.getWorkspace(), new RegexFileFilter("(.(?!unaligned)(?!unsigned))*(\\.apk|\\.ipa)"));

            List<FilePath> removeFiles = new LinkedList<FilePath>();
            for (FilePath file : files) {
                String fileAbsolutePath = file.getRemote();
                if (fileAbsolutePath.endsWith(".apk")) {
                    FilePath propertiesFile = new FilePath(
                            file.getParent().getParent(), "project.properties");
                    try {
                        if (propertiesFile.exists()) {
                            boolean isLibrary = propertiesFile.readToString()
                                    .contains("android.library=true");
                            if (isLibrary) {
                                removeFiles.add(file);
                            }
                        } else if (!fileAbsolutePath.endsWith("/build/apk/" + file.getName())) {
                            removeFiles.add(file);
                        }
                    } catch (Exception e) {
                        removeFiles.add(file);
                    }
                }
            }
            files.removeAll(removeFiles);
            return files;
        } catch (Exception e) {
            listener.getLogger().println(TAG + "Error: " + e.getMessage());
        }
        return new LinkedList<FilePath>();
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String server;
        private String apikey;

        public DescriptorImpl() {
            super(AndroidAppZonePublisher.class);
            load();
        }

        public FormValidation doCheckServer(@QueryParameter
                                            final String value)
                throws IOException, ServletException {
            if (value.length() != 0 && !value.startsWith("http")) {
                return FormValidation.error("Server needs to start with http");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Publish to AppZone";
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData)
                throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        public String getServer() {
            if (server == null || server.length() == 0) {
                server = DEFAULT_APPSERVER;
            }
            return server;
        }

        public void setServer(final String server) {
            this.server = server;
        }

        public String getApikey() {
            return apikey;
        }

        public void setApikey(final String apiKey) {
            this.apikey = apiKey;
        }
    }

    private static class DefaultTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(final X509Certificate[] arg0, final String arg1)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] arg0, final String arg1)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
